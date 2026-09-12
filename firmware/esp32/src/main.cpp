// CyberSixSeven — ESP32 firmware, v0.1
//
// Scope: join Wi-Fi, blink an external LED once connected, report status over
// serial at 115200 baud. Nothing else. MQTT over TLS arrives at v0.3.
//
// Everything here is non-blocking: `loop()` never calls delay() and never spins
// in a `while (!connected)` loop. A blocking wait is the single most common
// firmware bug — it looks fine on a good network and wedges the device on a bad
// one, with no serial output to explain why.

#include <Arduino.h>
#include <WiFi.h>

// secrets.h sits at the project root (firmware/esp32/secrets.h) because that is
// the exact path the repo .gitignore ignores. A quoted include resolves relative
// to this file, so no extra include path is needed. Copy secrets.h.example first.
#include "../secrets.h"

namespace {

// External LED, anode -> GPIO 2 through a current-limiting resistor, cathode -> GND.
constexpr uint8_t kLedPin = 2;

constexpr uint32_t kSerialBaud = 115200;
constexpr uint32_t kBlinkIntervalMs = 500;      // 1 Hz blink while connected
constexpr uint32_t kWifiRetryIntervalMs = 5000; // re-issue WiFi.begin() while down
constexpr uint32_t kStatusLogIntervalMs = 2000; // heartbeat line while down

// All timing uses unsigned subtraction (now - since >= interval), which stays
// correct across the ~49-day millis() rollover. Comparing (since + interval)
// against now does not.
uint32_t lastBlinkAtMs = 0;
uint32_t lastWifiAttemptAtMs = 0;
uint32_t lastStatusLogAtMs = 0;

bool ledOn = false;
int lastLoggedStatus = -1; // sentinel: no wl_status_t value has been logged yet

void setLed(bool on) {
  ledOn = on;
  digitalWrite(kLedPin, on ? HIGH : LOW);
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

void startWifiAttempt(uint32_t now) {
  lastWifiAttemptAtMs = now;
  Serial.printf("[wifi] connecting to SSID \"%s\"\n", WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

// Logs every state transition once, and retries the join at a fixed interval
// while disconnected. Returns true while the station has an association.
bool maintainWifi(uint32_t now) {
  const int status = WiFi.status();

  if (status != lastLoggedStatus) {
    lastLoggedStatus = status;
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

// Blinks only while connected; the LED is the "I am online" indicator, so a dark
// LED is meaningful information rather than an ambiguous state.
void updateLed(uint32_t now, bool connected) {
  if (!connected) {
    if (ledOn) {
      setLed(false);
    }
    return;
  }

  if (now - lastBlinkAtMs >= kBlinkIntervalMs) {
    lastBlinkAtMs = now;
    setLed(!ledOn);
  }
}

} // namespace

void setup() {
  Serial.begin(kSerialBaud);

  pinMode(kLedPin, OUTPUT);
  setLed(false);

  Serial.println();
  Serial.println("[boot] CyberSixSeven ESP32 firmware v0.1");
  Serial.printf("[boot] led gpio=%u serial=%lu baud\n",
                static_cast<unsigned>(kLedPin),
                static_cast<unsigned long>(kSerialBaud));

  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);

  const uint32_t now = millis();
  lastBlinkAtMs = now;
  lastStatusLogAtMs = now;
  startWifiAttempt(now);
}

void loop() {
  const uint32_t now = millis();
  const bool connected = maintainWifi(now);
  updateLed(now, connected);
}
