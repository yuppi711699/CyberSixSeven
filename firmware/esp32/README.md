# ESP32 firmware

PlatformIO project for the CyberSixSeven device.

**v0.1 scope:** connect to Wi-Fi, blink an external LED once connected, log status
over serial at 115200 baud. No MQTT, no TLS, no JSON — those arrive at v0.3.

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
pio run -t upload       # flash
pio device monitor      # serial at 115200
```

## Wiring

External LED on **GPIO 2**: anode → GPIO 2 through a current-limiting resistor
(220–330 Ω), cathode → GND.

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
