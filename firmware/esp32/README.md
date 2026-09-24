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
cp firmware/esp32/secrets.h.example firmware/esp32/secrets.h
                        #OR
cp secrets.h.example secrets.h
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
`intensity` must be an integer from 1 to 5. Anything outside that range is ignored: no LED, buzzer, motor, or ACK. 3 is the backend fallback when reward selection is unavailable.
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

### 1602 I2C — env `esp32dev-lcd1602`

Must flash **`-e esp32dev-lcd1602`**. Bare `pio run` is SSD1306 and this panel stays blank.

| Family | Chip | I2C | VCC |
|---|---|---|---|
| **PCF8574A backpack** (this project’s module) | `PCF8574A` / `PCF8574AT` | **`0x38–0x3F`, usually `0x3F`** | **5 V** |
| PCF8574 backpack | `PCF8574` / `PCF8574T` | `0x20–0x27`, usually `0x27` | **5 V** |
| Gravity DFR0464 | LCD + RGB, **not** PCF8574A | `0x3E` **and** RGB `0x60`/`0x6B`/`0x2D` | 3.3–5 V |

`0x3E` is a legal **PCF8574A** address. Gravity is only selected if a second RGB chip ACKs. Probe order: Gravity-with-RGB → PCF8574A (`0x3F` first) → PCF8574. Two backpack pinouts: YwRobot then MJKDZ.

| Backpack pin | ESP32 |
|---|---|
| VCC | **5 V** (3.3 V = empty glass even when I2C ACKs) |
| GND | GND |
| SCL | GPIO **22** |
| SDA | GPIO **21** |

Twist the blue contrast pot on the backpack if the backlight is on and there are no glyphs.

`pio run -e esp32dev-lcd1602 -t upload --upload-port /dev/cu.usbserial-0001`

Want serial `[lcd] pcf8574a addr=0x3F rgb=0x00 sda=21 scl=22` and boot text `pcf8574a ok`. Gravity would log `gravity` + a non-zero `rgb`. Scan dump if nothing ACKs.

Boot/idle (16×2 wrap of “Hi, beautiful student. My name is ${PROFILE_NAME}”; default `Bob` in `secrets.h`):

```
Hi, beautiful
My name is Bob
```

`event=correct`: `:)  NICE WORK!`. `event=incorrect`: `almost there` / `try once more`. `src/lcd1602.cpp` over `Wire`.

Incorrect reactions are gentle (soft LED + rising tone + encouraging face/text). Correct
reactions are upbeat. Intensity is clamped to 1–5.

## LCD type registry

Compile-time (`-D C67_LCD_TYPE=<int>`), not `secrets.h`. Bare `pio run` is type `1`.

| `C67_LCD_TYPE` | PlatformIO env | Panel | VCC | I2C | Driver |
|---|---|---|---|---|---|
| `1` | `esp32dev` (default) | 0.96" SSD1306 128×64 | 3.3 V | `0x3C` / `0x3D` | `src/ssd1306.cpp` |
| `2` | `esp32dev-lcd1602` | 16×2 PCF8574A (`0x3F`) / PCF8574 (`0x27`); Gravity only if `0x3E`+RGB | **5 V** / 3.3–5 V | see row | `src/lcd1602.cpp` |
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