# ESP32 firmware

PlatformIO project for the CyberSixSeven device.

**v0.3 scope:** join Wi-Fi, wait for NTP, connect to Mosquitto over mTLS, subscribe
to `devices/{deviceId}/commands` at QoS 1, run one LED/buzzer/motor/OLED-face
effect per new `commandId`, persist a 64-entry NVS ring, and ACK on
`devices/{deviceId}/status`.

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
pio run -t upload --upload-port /dev/cu.usbserial-0001 
|| pio run -d firmware/esp32 -t upload --upload-port /dev/cu.usbserial-0001
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
| SSD1306 OLED SDA | **21** | I2C | 3.3 V module, 4.7 kΩ pull-ups usually on the board |
| SSD1306 OLED SCL | **22** | I2C | address `0x3C` (fallback `0x3D`) |

### 0.96" SSD1306 128×64 (4-pin I2C)

| OLED pin | ESP32 |
|---|---|
| VCC | **3.3 V** (not 5 V unless the module is marked 5 V-tolerant) |
| GND | GND |
| SCL / SCK / CLK | GPIO **22** |
| SDA | GPIO **21** |

Pin order on cheap modules is sometimes VCC-GND-SCL-SDA, sometimes GND-VCC-SCL-SDA. Match labels, not left-to-right. Shared ground with the DevKit. No extra PlatformIO library — the driver is `src/ssd1306.cpp` over Arduino `Wire`.

Idle: eyes look around and blink. `event=correct` (student passed): bounce + happy squint + sparkle. `event=incorrect`: gentle sway + hopeful blink, not an X or a frown.

Incorrect reactions are gentle (soft LED + rising tone + encouraging face). Correct
reactions are upbeat. Intensity is clamped to 1–5.

## Build

```bash
pio run -d firmware/esp32
```

Serial must show `[ntp] synchronized` **before** `[mqtt] starting tls connection`.
Unauthenticated MQTT clients are rejected by Mosquitto; prove that from the
broker/Paho tests, not by disabling TLS verification on the ESP32.

If serial shows `(-9984) X509 - Certificate verification failed` after NTP, the
broker cert SAN lacks `DNS:<MQTT_BROKER_HOST>`. ESP32 mbedTLS 2.28 ignores IP
SANs and only matches dNSName. Do not call `setInsecure()`. Reissue (same CA):

```bash
ISSUE_BROKER_ONLY=1 infra/local/mosquitto/certs/generate-certs.sh
docker compose restart mosquitto
```

Then tap EN. No firmware reflash if `secrets.h` already has this CA.

## Troubleshoot 

If you see encoded responce from esp:

If 115200 + --dtr 0 --rts 0 + EN still loops
Chip is crash-resetting, not a monitor bug:

1. Hold BOOT, tap EN, release BOOT, re-flash:
```bash
pio run -t upload --upload-port /dev/cu.usbserial-0001
```
2. Then monitor with --dtr 0 --rts 0 and tap EN.
3. If you still only get ROM at 74880, flash didn’t stick or the board is browning out (bad cable / USB hub / motor rail). Swap to a data cable, plug into the Mac directly, no motor/buzzer yet.
--dtr 0 --rts 0 first. That’s the CH340/macOS gotcha.