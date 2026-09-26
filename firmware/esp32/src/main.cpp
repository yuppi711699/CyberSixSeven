// CyberSixSeven — ESP32 firmware, v0.3
//
// Wi-Fi + NTP + mTLS MQTT. Commands arrive on devices/{id}/commands.
// Physical effects run from loop(); the MQTT callback only copies fields.
// Duplicate commandId values (including after reboot) skip the effect and still ACK.

#include <Arduino.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include <PubSubClient.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <time.h>

#include "face.h"
#include "lcd_type.h"
#include "reliability.h"
#include "../secrets.h"

#ifndef C67_SECRETS_HAS_MQTT
constexpr char DEVICE_ID[] = "esp32-dev-001";
constexpr char MQTT_BROKER_HOST[] = "192.168.1.10";
constexpr uint16_t MQTT_BROKER_PORT = 8883;
constexpr char CA_CERT[] = "";
constexpr char CLIENT_CERT[] = "";
constexpr char CLIENT_KEY[] = "";
#endif

#ifndef PROFILE_NAME
constexpr char PROFILE_NAME[] = "Bob";
#endif

namespace {

constexpr uint8_t kLedPin = 2;
constexpr uint8_t kBuzzerPin = 4;
constexpr uint8_t kMotorPin = 5;

constexpr uint32_t kSerialBaud = 115200;
constexpr uint32_t kWifiRetryIntervalMs = 5000;
constexpr uint32_t kStatusLogIntervalMs = 2000;
constexpr uint32_t kNtpWaitLogIntervalMs = 2000;
constexpr time_t kMinUnixTime = 1700000000;
constexpr uint16_t kMqttBuffer = 512;
constexpr uint8_t kLedcChannel = 0;
constexpr uint8_t kLedcResolution = 8;

constexpr char kNvsNamespace[] = "c67cmd";
constexpr char kNvsBlobKey[] = "ring";

using c67::CommandRing;
using c67::NetPhase;
using c67::PayloadReject;
using c67::kMqttBackoffMaxMs;
using c67::kMqttBackoffMinMs;

struct PendingCommand {
  bool ready;
  char commandId[37];
  char submissionId[37];
  char event[16];
  int intensity;
};

struct Effect {
  bool active;
  bool gentle;
  uint8_t intensity;
  uint32_t startedAtMs;
  uint32_t durationMs;
  uint32_t lastToggleMs;
  bool ledOn;
};

WiFiClientSecure tlsClient;
PubSubClient mqtt(tlsClient);
Preferences prefs;

NetPhase phase = NetPhase::Wifi;
CommandRing ring{};
PendingCommand pending{};
Effect effect{};

uint32_t lastBlinkAtMs = 0;
uint32_t lastWifiAttemptAtMs = 0;
uint32_t lastStatusLogAtMs = 0;
uint32_t lastNtpLogAtMs = 0;
uint32_t nextMqttAttemptAtMs = 0;
uint32_t mqttBackoffMs = kMqttBackoffMinMs;
int lastLoggedWifi = -1;
bool ledOnIdle = false;
bool ntpStarted = false;
bool subscribed = false;

char commandsTopic[64];
char statusTopic[64];

void setLed(bool on) {
  digitalWrite(kLedPin, on ? HIGH : LOW);
}

void motor(bool on) {
  digitalWrite(kMotorPin, on ? HIGH : LOW);
}

void buzzerStop() {
  ledcWriteTone(kLedcChannel, 0);
}

void buzzerTone(uint32_t freqHz) {
  ledcWriteTone(kLedcChannel, freqHz);
}

const char* wifiStatusName(int status) {
  switch (status) {
    case WL_IDLE_STATUS:     return "IDLE";
    case WL_NO_SSID_AVAIL:   return "NO_SSID_AVAIL";
    case WL_SCAN_COMPLETED:  return "SCAN_COMPLETED";
    case WL_CONNECTED:       return "CONNECTED";
    case WL_CONNECT_FAILED:  return "CONNECT_FAILED";
    case WL_CONNECTION_LOST: return "CONNECTION_LOST";
    case WL_DISCONNECTED:    return "DISCONNECTED";
    default:                 return "UNKNOWN";
  }
}

void loadRing() {
  prefs.begin(kNvsNamespace, false);
  const size_t n = prefs.getBytes(kNvsBlobKey, &ring, sizeof(ring));
  if (n != sizeof(ring) || !c67::ringValid(ring)) {
    ring = CommandRing{};
  }
}

void saveRing() {
  prefs.putBytes(kNvsBlobKey, &ring, sizeof(ring));
}

bool ringContains(const char* commandId) {
  return c67::ringContains(ring, commandId);
}

void ringRemember(const char* commandId) {
  c67::ringRemember(ring, commandId);
  saveRing();
}

void copyField(char* dest, size_t destSize, const char* src) {
  if (src == nullptr) {
    dest[0] = '\0';
    return;
  }
  strncpy(dest, src, destSize - 1);
  dest[destSize - 1] = '\0';
}

int clampIntensity(int value) {
  if (value < 1) {
    return 1;
  }
  if (value > 5) {
    return 5;
  }
  return value;
}

void startEffect(bool gentle, int intensity) {
  effect.active = true;
  effect.gentle = gentle;
  effect.intensity = static_cast<uint8_t>(clampIntensity(intensity));
  effect.startedAtMs = millis();
  effect.durationMs = 400u * effect.intensity + (gentle ? 400u : 200u);
  effect.lastToggleMs = effect.startedAtMs;
  effect.ledOn = false;
  setLed(false);
  motor(!gentle);
  buzzerTone(gentle ? 880 : 1400);
  if (gentle) {
    facePlayEncourage(effect.intensity);
  } else {
    facePlayHappy(effect.intensity);
  }
}

void stopEffect() {
  effect.active = false;
  motor(false);
  buzzerStop();
  setLed(false);
  ledOnIdle = false;
}

void updateEffect(uint32_t now) {
  if (!effect.active) {
    return;
  }
  if (now - effect.startedAtMs >= effect.durationMs) {
    stopEffect();
    return;
  }
  const uint32_t interval = effect.gentle ? 180 : 90;
  if (now - effect.lastToggleMs >= interval) {
    effect.lastToggleMs = now;
    effect.ledOn = !effect.ledOn;
    setLed(effect.ledOn);
    if (effect.gentle) {
      buzzerTone(effect.ledOn ? 988 : 784);
      motor(false);
    } else {
      buzzerTone(effect.ledOn ? 1568 : 1319);
      motor(effect.ledOn);
    }
  }
}

void publishAck(const char* commandId, const char* submissionId) {
  char payload[192];
  snprintf(
      payload,
      sizeof(payload),
      "{\"deviceId\":\"%s\",\"commandId\":\"%s\",\"submissionId\":\"%s\",\"status\":\"handled\"}",
      DEVICE_ID,
      commandId,
      submissionId);
  mqtt.publish(statusTopic, payload, false);
  Serial.printf("[ack] commandId=%s submissionId=%s\n", commandId, submissionId);
}

void handlePendingCommand() {
  if (!pending.ready) {
    return;
  }
  pending.ready = false;
  const bool duplicate = ringContains(pending.commandId);
  if (!duplicate) {
    const bool gentle = strcmp(pending.event, "incorrect") == 0;
    startEffect(gentle, pending.intensity);
    ringRemember(pending.commandId);
    Serial.printf(
        "[cmd] new commandId=%s event=%s intensity=%d\n",
        pending.commandId,
        pending.event,
        pending.intensity);
  } else {
    Serial.printf("[cmd] duplicate commandId=%s skip-effect\n", pending.commandId);
  }
  publishAck(pending.commandId, pending.submissionId);
}

void onMqttMessage(char* topic, byte* payload, unsigned int length) {
  JsonDocument doc;
  const DeserializationError err = deserializeJson(doc, payload, length);
  if (err) {
    Serial.printf("[mqtt] json error=%s\n", err.c_str());
    return;
  }
  if (!doc["commandId"].is<const char*>()
      || !doc["submissionId"].is<const char*>()
      || !doc["event"].is<const char*>()
      || !doc["intensity"].is<int>()) {
    Serial.println("[mqtt] missing command fields");
    return;
  }
  const char* event = doc["event"].as<const char*>();
  const bool intensityIsInt = doc["intensity"].is<int>();
  const int rawIntensity = intensityIsInt ? doc["intensity"].as<int>() : 0;
  const PayloadReject reject = c67::acceptCommand(
      doc["commandId"].is<const char*>() ? doc["commandId"].as<const char*>() : nullptr,
      doc["submissionId"].is<const char*>() ? doc["submissionId"].as<const char*>() : nullptr,
      doc["event"].is<const char*>() ? event : nullptr,
      intensityIsInt,
      rawIntensity);
  if (reject == PayloadReject::MissingField) {
    Serial.println("[mqtt] missing command fields");
    return;
  }
  if (reject == PayloadReject::UnknownEvent) {
    Serial.println("[mqtt] unknown event");
    return;
  }
  if (reject == PayloadReject::Intensity) {
    Serial.printf("[mqtt] intensity out of range=%d\n", rawIntensity);
    return;
  }
  copyField(pending.commandId, sizeof(pending.commandId), doc["commandId"].as<const char*>());
  copyField(pending.submissionId, sizeof(pending.submissionId), doc["submissionId"].as<const char*>());
  copyField(pending.event, sizeof(pending.event), event);
  pending.intensity = static_cast<uint8_t>(rawIntensity);
  pending.ready = true;
}

void startWifiAttempt(uint32_t now) {
  lastWifiAttemptAtMs = now;
  Serial.printf("[wifi] connecting to SSID \"%s\"\n", WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

bool maintainWifi(uint32_t now) {
  const int status = WiFi.status();
  if (status != lastLoggedWifi) {
    lastLoggedWifi = status;
    if (status == WL_CONNECTED) {
      Serial.printf("[wifi] connected - ip=%s rssi=%d dBm\n",
                    WiFi.localIP().toString().c_str(), WiFi.RSSI());
    } else {
      Serial.printf("[wifi] status=%s\n", wifiStatusName(status));
    }
  }
  if (status == WL_CONNECTED) {
    return true;
  }
  if (now - lastWifiAttemptAtMs >= kWifiRetryIntervalMs) {
    startWifiAttempt(now);
  }
  if (now - lastStatusLogAtMs >= kStatusLogIntervalMs) {
    lastStatusLogAtMs = now;
    Serial.printf("[wifi] waiting - status=%s uptime=%lus\n",
                  wifiStatusName(status), static_cast<unsigned long>(now / 1000));
  }
  return false;
}

bool ntpReady(uint32_t now) {
  if (!ntpStarted) {
    configTime(0, 0, "pool.ntp.org", "time.nist.gov");
    ntpStarted = true;
    Serial.println("[ntp] synchronizing before TLS");
  }
  if (time(nullptr) > kMinUnixTime) {
    Serial.printf("[ntp] synchronized unix=%ld\n", static_cast<long>(time(nullptr)));
    return true;
  }
  if (now - lastNtpLogAtMs >= kNtpWaitLogIntervalMs) {
    lastNtpLogAtMs = now;
    Serial.println("[ntp] waiting for clock before TLS");
  }
  return false;
}

void configureTls() {
  if (CA_CERT[0] == '\0' || CLIENT_CERT[0] == '\0' || CLIENT_KEY[0] == '\0') {
    Serial.println("[tls] device certificates missing — copy generate-certs.sh output into secrets.h");
    return;
  }
  tlsClient.setCACert(CA_CERT);
  tlsClient.setCertificate(CLIENT_CERT);
  tlsClient.setPrivateKey(CLIENT_KEY);
}

bool connectMqtt(uint32_t now) {
  if (now < nextMqttAttemptAtMs) {
    return false;
  }
  Serial.printf("[mqtt] starting tls connection host=%s port=%u\n",
                MQTT_BROKER_HOST, static_cast<unsigned>(MQTT_BROKER_PORT));
  mqtt.setServer(MQTT_BROKER_HOST, MQTT_BROKER_PORT);
  mqtt.setBufferSize(kMqttBuffer);
  mqtt.setCallback(onMqttMessage);
  const bool ok = mqtt.connect(DEVICE_ID);
  if (!ok) {
    mqttBackoffMs = c67::nextMqttBackoff(mqttBackoffMs);
    const uint32_t jitter = static_cast<uint32_t>(random(0, mqttBackoffMs / 4 + 1));
    nextMqttAttemptAtMs = now + mqttBackoffMs + jitter;
    Serial.printf("[mqtt] connect failed rc=%d retry-ms=%lu\n",
                  mqtt.state(), static_cast<unsigned long>(mqttBackoffMs + jitter));
    return false;
  }
  subscribed = mqtt.subscribe(commandsTopic, 1);
  mqttBackoffMs = kMqttBackoffMinMs;
  nextMqttAttemptAtMs = now;
  Serial.printf("[mqtt] connected subscribed=%d topic=%s\n", subscribed ? 1 : 0, commandsTopic);
  return mqtt.connected();
}

void maintainMqtt(uint32_t now) {
  if (mqtt.connected()) {
    if (!subscribed) {
      subscribed = mqtt.subscribe(commandsTopic, 1);
    }
    mqtt.loop();
    return;
  }
  subscribed = false;
  phase = NetPhase::Mqtt;
  connectMqtt(now);
}

void updateIdleLed(uint32_t now, bool online) {
  if (effect.active) {
    return;
  }
  if (!online) {
    if (ledOnIdle) {
      setLed(false);
      ledOnIdle = false;
    }
    return;
  }
  if (now - lastBlinkAtMs >= 500) {
    lastBlinkAtMs = now;
    ledOnIdle = !ledOnIdle;
    setLed(ledOnIdle);
  }
}

}  // namespace

void setup() {
  Serial.begin(kSerialBaud);
  pinMode(kLedPin, OUTPUT);
  pinMode(kBuzzerPin, OUTPUT);
  pinMode(kMotorPin, OUTPUT);
  setLed(false);
  motor(false);
  ledcSetup(kLedcChannel, 2000, kLedcResolution);
  ledcAttachPin(kBuzzerPin, kLedcChannel);
  ledcWriteTone(kLedcChannel, 0);

  snprintf(commandsTopic, sizeof(commandsTopic), "devices/%s/commands", DEVICE_ID);
  snprintf(statusTopic, sizeof(statusTopic), "devices/%s/status", DEVICE_ID);

  Serial.println();
  Serial.println("[boot] CyberSixSeven ESP32 firmware v0.3");
#if C67_LCD_TYPE == C67_LCD_ST7789
  Serial.printf("[boot] device=%s lcd=%s profile=%s led=%u buzzer=%u motor=%u sck=%u mosi=%u dc=%u cs=%u rst=%u blk=%u\n",
                DEVICE_ID,
                faceLcdName(),
                PROFILE_NAME,
                static_cast<unsigned>(kLedPin),
                static_cast<unsigned>(kBuzzerPin),
                static_cast<unsigned>(kMotorPin),
                static_cast<unsigned>(kTftSckPin),
                static_cast<unsigned>(kTftMosiPin),
                static_cast<unsigned>(kTftDcPin),
                static_cast<unsigned>(kTftCsPin),
                static_cast<unsigned>(kTftRstPin),
                static_cast<unsigned>(kTftBlPin));
#else
  Serial.printf("[boot] device=%s lcd=%s profile=%s led=%u buzzer=%u motor=%u sda=%u scl=%u\n",
                DEVICE_ID,
                faceLcdName(),
                PROFILE_NAME,
                static_cast<unsigned>(kLedPin),
                static_cast<unsigned>(kBuzzerPin),
                static_cast<unsigned>(kMotorPin),
                static_cast<unsigned>(kLcdSdaPin),
                static_cast<unsigned>(kLcdSclPin));
#endif

  faceSetProfileName(PROFILE_NAME);
  faceBegin();
  loadRing();
  configureTls();
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);

  const uint32_t now = millis();
  lastBlinkAtMs = now;
  lastStatusLogAtMs = now;
  lastNtpLogAtMs = now;
  startWifiAttempt(now);
}

void loop() {
  const uint32_t now = millis();
  const bool wifiUp = maintainWifi(now);
  phase = c67::phaseAfterWifi(wifiUp, phase);
  if (!wifiUp) {
    ntpStarted = false;
    subscribed = false;
    updateEffect(now);
    updateIdleLed(now, false);
    faceUpdate(now, false);
    return;
  }

  if (phase == NetPhase::Ntp) {
    if (!ntpReady(now)) {
      updateEffect(now);
      updateIdleLed(now, false);
      faceUpdate(now, false);
      return;
    }
    phase = c67::phaseAfterClock(true, phase);
  }

  if (phase == NetPhase::Mqtt) {
    if (connectMqtt(now)) {
      phase = NetPhase::Ready;
    }
  } else {
    maintainMqtt(now);
    if (mqtt.connected()) {
      phase = NetPhase::Ready;
    }
  }

  handlePendingCommand();
  updateEffect(now);
  const bool online = phase == NetPhase::Ready && mqtt.connected();
  updateIdleLed(now, online);
  faceUpdate(now, online);
}
