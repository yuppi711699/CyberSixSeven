# ESP32 firmware

PlatformIO project for the CyberSixSeven device.

**v0.3 scope:** join Wi-Fi, wait for NTP, connect to Mosquitto over mTLS, subscribe
to `devices/{deviceId}/commands` at QoS 1, run one LED/buzzer/motor/LCD
effect per new `commandId`, persist a 64-entry NVS ring, and ACK on
`devices/{deviceId}/status`.

## Board identity

| Field | Value |
|---|---|
| Board | ESP32 DevKit V1 (30-pin), ESP-WROOM-32 |
| PlatformIO env | `esp32dev` (SSD1306) or `esp32dev-lcd1602` (1602) in `platformio.ini` |
| LCD type | `-D C67_LCD_TYPE=1` SSD1306 · `-D C67_LCD_TYPE=2` HD44780 1602 |
| Platform pin | `espressif32@7.1.3` |
| Serial | 115200 8N1 |
| MQTT | TLS 8883, client certificates required |
| Libraries | ArduinoJson 7.4.3, PubSubClient 2.8 |

```bash
## XXXX=0001
pio run -e esp32dev -t upload --upload-port /dev/cu.usbserial-XXXX
pio run -e esp32dev-lcd1602 -t upload --upload-port /dev/cu.usbserial-0001
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
| SSD1306 OLED / 1602 backpack SDA | **21** | I2C | same bus; only one panel wired |
| SSD1306 OLED / 1602 backpack SCL | **22** | I2C | SSD1306 `0x3C`/`0x3D` · 1602 backpack `0x27`/`0x3F` |

Pick the PlatformIO env that matches the glass. `pio run` with no `-e` is SSD1306.

### 0.96" SSD1306 128×64 (4-pin I2C) — env `esp32dev`

| OLED pin | ESP32 |
|---|---|
| VCC | **3.3 V** (not 5 V unless the module is marked 5 V-tolerant) |
| GND | GND |
| SCL / SCK / CLK | GPIO **22** |
| SDA | GPIO **21** |

Pin order on cheap modules is sometimes VCC-GND-SCL-SDA, sometimes GND-VCC-SCL-SDA. Match labels, not left-to-right. Shared ground with the DevKit. No extra PlatformIO library — the driver is `src/ssd1306.cpp` over Arduino `Wire`.

Idle: eyes look around and blink. `event=correct` (student passed): bounce + happy squint + sparkle. `event=incorrect`: gentle sway + hopeful blink, not an X or a frown.

### 1602 HD44780 + I2C backpack — env `esp32dev-lcd1602`

4-pin PCF8574/PCF8574A backpack on a 16×2. Same SDA/SCL as the OLED. **VCC is 5 V** on almost every backpack; 3.3 V is a blank/washed-out screen, not a firmware bug. Twist the backpack contrast pot if you have backlight but no glyphs.

| Backpack pin | ESP32 |
|---|---|
| VCC | **5 V** |
| GND | GND |
| SCL | GPIO **22** |
| SDA | GPIO **21** |

`pio run -e esp32dev-lcd1602 -t upload --upload-port /dev/cu.usbserial-0001`

Probes `0x27`, then `0x3F`, then the rest of the PCF8574 range. Serial `[lcd] hd44780 16x2 addr=0x..` on success; an I2C scan if nothing ACKs.

Idle: `CyberSixSeven` / `linking...` then `ready`. `event=correct`: `:)  NICE WORK!`. `event=incorrect`: `almost there` / `try once more` (encouraging, not “wrong”). Driver is `src/lcd1602.cpp` over `Wire` — no LiquidCrystal_I2C.

Incorrect reactions are gentle (soft LED + rising tone + encouraging face/text). Correct
reactions are upbeat. Intensity is clamped to 1–5.

## LCD type registry

Compile-time (`-D C67_LCD_TYPE=<int>`), not `secrets.h`. Bare `pio run` is type `1`.

| `C67_LCD_TYPE` | PlatformIO env | Panel | VCC | I2C | Driver |
|---|---|---|---|---|---|
| `1` | `esp32dev` (default) | 0.96" SSD1306 128×64 | 3.3 V | `0x3C` / `0x3D` | `src/ssd1306.cpp` |
| `2` | `esp32dev-lcd1602` | HD44780 1602 + PCF8574 backpack | **5 V** | `0x27` / `0x3F` | `src/lcd1602.cpp` |
| `3+` | `esp32dev-<name>` | next panel | per module | per module | `src/<chip>.cpp` |

SDA **21** / SCL **22** for every I2C panel. One module on the bus. `face.cpp` `#if`s on `C67_LCD_TYPE`. No extra PlatformIO libraries.

### Adding LCD type N

1. Next unused int in `src/lcd_type.h` (`#define C67_LCD_… N`).
2. New env in `platformio.ini` that `extends = env:esp32dev` and **repeats** both `-D MQTT_MAX_PACKET_SIZE=512` and `-D C67_LCD_TYPE=N` (`extends` replaces `build_flags`).
3. `src/<chip>.h/.cpp` over `Wire`. Probe addresses at `Begin()`, log `[lcd] … addr=0x..` or an I2C scan. `delay()` only in `Begin()`.
4. `face.cpp` / `faceLcdName()`: idle + `correct` (upbeat) + `incorrect` (encouraging, never “wrong”).
5. Wiring table + this registry row + the upload `-e` in this README.
6. `pio run -d firmware/esp32 -e esp32dev -e esp32dev-lcd1602 -e esp32dev-<name>` — all three must stay green.

## Build

```bash
pio run -d firmware/esp32 -e esp32dev
pio run -d firmware/esp32 -e esp32dev-lcd1602
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
pio run -e esp32dev-lcd1602 -t upload --upload-port /dev/cu.usbserial-0001
# or -e esp32dev for the SSD1306 binary — default env is SSD1306, not 1602
```
2. Then monitor with --dtr 0 --rts 0 and tap EN.
3. If you still only get ROM at 74880, flash didn’t stick or the board is browning out (bad cable / USB hub / motor rail). Swap to a data cable, plug into the Mac directly, no motor/buzzer yet.
--dtr 0 --rts 0 first. That’s the CH340/macOS gotcha.