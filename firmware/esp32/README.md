# ESP32 firmware

PlatformIO project for the CyberSixSeven device.

**v0.1 scope:** connect to Wi-Fi, blink an external LED once connected, log status
over serial at 115200 baud. No MQTT, no TLS, no JSON — those arrive at v0.3.

**v0.2 scope:** keep the v0.1 firmware green; document and wire LED / buzzer / motor
with safe drivers and shared ground. Firmware still drives **LED only**. Buzzer and
motor GPIO constants land with the MQTT command path in v0.3 — do not add
MQTT, ArduinoJson, or PubSubClient here.

## Board identity

| Field | Value |
|---|---|
| Board | ESP32 DevKit V1 (30-pin), ESP-WROOM-32 |
| PlatformIO env | `esp32dev` in `platformio.ini` |
| Platform pin | `espressif32@7.1.3` |
| Serial | 115200 8N1 |
| Upload | **require an explicit USB serial port** (e.g. `/dev/cu.usbserial-*`). Do not let PlatformIO auto-pick Bluetooth headsets such as `/dev/cu.BeatsFlex`. |

```bash
## XXXX=0001
pio run -t upload --upload-port /dev/cu.usbserial-XXXX
# pio device monitor --port /dev/cu.usbserial-XXXX -b 115200
pio device monitor --port /dev/cu.usbserial-0001 -b 115200 --dtr 0 --rts 0
```

## Setup

```bash
cp firmware/esp32/secrets.h.example firmware/esp32/secrets.h
# edit secrets.h with the real SSID and password
```

`firmware/esp32/secrets.h` is gitignored at the repo root and must never be
committed. Only `secrets.h.example`, with placeholder values, is tracked.

## Build and run

```bash
cd firmware/esp32
pio run                 # compile
pio run -t upload --upload-port /dev/cu.usbserial-XXXX
pio device monitor --port /dev/cu.usbserial-XXXX -b 115200
```

## GPIO map (reserved for physical outputs)

Pins below are the project assignment for the enclosure. Only GPIO 2 is used in
firmware today (`kLedPin` in `src/main.cpp`).

| Output | GPIO | Direction | Driver | Notes |
|---|---|---|---|---|
| External LED | **2** | digital out | series resistor only | Anode → 220–330 Ω → GPIO 2; cathode → GND. Active HIGH. |
| Passive buzzer | **4** | digital / PWM out | NPN (2N2222 / 2N3904) or logic MOSFET low-side | GPIO → 1 kΩ base/gate → transistor; buzzer + → **5 V** (or 3.3 V if rated); buzzer − → collector/drain; emitter/source → GND. Never drive a buzzer from the GPIO pin alone. |
| Vibration / DC motor | **5** | digital out | N-channel MOSFET (e.g. IRLZ44N / AO3400) + flyback diode | GPIO → 100–220 Ω → gate; motor + → **5 V**; motor − → drain; source → GND; diode across motor (cathode to +5 V). PWM later if needed — not in v0.1/v0.2 firmware. |

Avoid strapping/boot-sensitive pins for outputs (0, 12, 15) and avoid UART0 (1/3) used by the USB serial console.

## Driver notes and shared ground

- All loads share one **common GND** with the ESP32 DevKit GND pin. Floating grounds cause flaky Wi-Fi and random resets that look like software bugs.
- LED draws milliamps from the GPIO — fine. Buzzer and motor **must** use a transistor/MOSFET so the GPIO only sees base/gate current.
- Prefer a separate **5 V** rail (USB hub / regulated supply) for motor + buzzer. Do not hang a motor off the DevKit’s 3.3 V pin.
- Add a **100 µF–470 µF** electrolytic across the 5 V rail near the motor, and a **0.1 µF** ceramic near the ESP32 3.3 V/GND, to absorb inductive spikes.
- Flyback diode on the motor is mandatory. Omitting it can brown out or kill the regulator.

## Power and brownout limits

| Limit | Guidance |
|---|---|
| ESP32 3.3 V rail | Keep GPIO loads under ~12 mA per pin; total chip budget ~40–50 mA beyond Wi-Fi peaks. |
| Motor / buzzer | Peak currents of hundreds of mA — supply from 5 V through the driver, not from GPIO. |
| Brownout | If the board resets when the motor spins up, supply voltage sagged. Fix: better USB cable/port, dedicated 5 V supply, bulk cap on motor rail, shorter motor duty. |
| Wi-Fi peaks | TX current spikes; a starving USB port + motor start = reset. Prove LED alone first, then add one load at a time. |

## Per-output manual exercise checklist

Run with firmware that only blinks the LED after Wi-Fi connects. Exercise buzzer/motor with a temporary jumper to 3.3 V through the driver (or a one-shot sketch later) — **do not claim pass without a physical board**.

| # | Check | Pass criteria | Result (fill on hardware) |
|---|---|---|---|
| 1 | Compile | `pio run -d firmware/esp32` succeeds | |
| 2 | Upload | Explicit `--upload-port` to the ESP32 USB-UART | |
| 3 | Serial | Boot banner + Wi-Fi connect lines at 115200 | |
| 4 | LED | Blinks ~1 Hz while associated; dark when link drops | |
| 5 | Buzzer | Short tone via driver + shared GND; no ESP32 reset | |
| 6 | Motor | Brief spin via MOSFET + diode + shared GND; no brownout/reset | |
| 7 | Combined | LED blink + brief buzzer or motor pulse; Wi-Fi stays up | |

Physical checks 4–7 are **manual on real hardware**. Document pass/fail here when exercised; compile + CAD proves below stay automated.

## Expected serial output

```
[boot] CyberSixSeven ESP32 firmware v0.1
[boot] led gpio=2 serial=115200 baud
[wifi] connecting to SSID "..."
[wifi] status=DISCONNECTED
[wifi] connected - ip=192.168.1.42 rssi=-54 dBm
```

The LED blinks at 1 Hz from the moment the station associates, and goes dark if
the link drops.

## Pinned versions

`platform = espressif32@7.1.3` is pinned exactly in `platformio.ini`. A floating
`espressif32` resolves to whatever is newest at install time and swaps the Arduino
core underneath the firmware. 7.1.3 declares `"platformio": "^6"`, so it works with
the PlatformIO Core 6.2.0 pinned in `documents/05-tech-stack.md`.

## Timing

`loop()` contains no `delay()` and no blocking `while (!connected)` wait. All
timing is `millis()`-based unsigned subtraction (`now - since >= interval`), which
stays correct across the ~49-day rollover.
