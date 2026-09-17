# ESP32 firmware

PlatformIO project for the CyberSixSeven device.

**v0.3 scope:** join Wi-Fi, wait for NTP, connect to Mosquitto over mTLS, subscribe
to `devices/{deviceId}/commands` at QoS 1, run one LED/buzzer/motor effect per new
`commandId`, persist a 64-entry NVS ring, and ACK on `devices/{deviceId}/status`.

## Board identity

| Field | Value |
|---|---|
| Board | ESP32 DevKit V1 (30-pin), ESP-WROOM-32 |
| PlatformIO env | `esp32dev` in `platformio.ini` |
| Platform pin | `espressif32@7.1.3` |
| Serial | 115200 8N1 |
| MQTT | TLS 8883, client certificates required |
| Libraries | ArduinoJson 7.4.3, PubSubClient 2.8 |

```bash
## XXXX=0001
pio run -t upload --upload-port /dev/cu.usbserial-XXXX
pio device monitor --port /dev/cu.usbserial-0001 -b 115200 --dtr 0 --rts 0
```

## Setup

```bash
cp firmware/esp32/secrets.h.example firmware/esp32/secrets.h
# edit Wi-Fi, DEVICE_ID, MQTT_BROKER_HOST
infra/local/mosquitto/certs/generate-certs.sh
# paste ca.crt, esp32-dev-001.crt, and esp32-dev-001.key into secrets.h
```

`firmware/esp32/secrets.h` is gitignored. Generated broker/device keys are gitignored.

## MQTT contract

Command (QoS 1) on `devices/{deviceId}/commands`:

```json
{"commandId":"...","event":"correct","intensity":3,"submissionId":"..."}
```

ACK (PubSubClient QoS 0 publish) on `devices/{deviceId}/status`:

```json
{"deviceId":"...","commandId":"...","submissionId":"...","status":"handled"}
```

`commandId` is unchanged from the outbox through SNS/SQS/MQTT/DynamoDB/ACK.
Duplicates, including after reboot, skip the physical effect and still ACK.
Do not claim end-to-end exactly-once transport.

## GPIO map

| Output | GPIO | Direction | Driver |
|---|---|---|---|
| External LED | **2** | digital out | series resistor |
| Passive buzzer | **4** | PWM out | NPN/MOSFET low-side |
| Vibration / DC motor | **5** | digital out | MOSFET + flyback diode |

Incorrect reactions are gentle (soft LED + rising tone). Correct reactions are
upbeat. Intensity is clamped to 1–5.

## Build

```bash
pio run -d firmware/esp32
```

Serial must show `[ntp] synchronized` **before** `[mqtt] starting tls connection`.
Unauthenticated MQTT clients are rejected by Mosquitto; prove that from the
broker/Paho tests, not by disabling TLS verification on the ESP32.
