#pragma once

#include <Arduino.h>

// Selected by the PlatformIO env in platformio.ini:
//   -e esp32dev            → C67_LCD_TYPE=C67_LCD_SSD1306
//   -e esp32dev-lcd1602    → C67_LCD_TYPE=C67_LCD_LCD1602
// Integer macros so #if works. Do not pass a string to -D C67_LCD_TYPE.

#define C67_LCD_SSD1306 1
#define C67_LCD_LCD1602 2
#define C67_LCD_ST7789 3
// Type 2: PCF8574A (0x38-0x3F, default 0x3F) / PCF8574 (0x20-0x27) backpack,
// then Gravity DFR0464 only if 0x3E AND RGB both ACK.
// Type 3: 1.54" 240x240 ST7789, 4-wire SPI. Not on the I2C bus. Next panel: 4.

#ifndef C67_LCD_TYPE
#define C67_LCD_TYPE C67_LCD_SSD1306
#endif

// Both panels share the DevKit I2C pins. Gravity DFR0464 accepts 3.3–5 V;
// PCF8574 backpacks want 5 V. SDA/SCL stay 3.3 V either way.
constexpr uint8_t kLcdSdaPin = 21;
constexpr uint8_t kLcdSclPin = 22;

// Type 3 only. VSPI defaults, clear of LED 2 / buzzer 4 / motor 5 and of the
// flash and strapping pins. BLK is driven high: this module's backlight is
// off when the pin floats or is low.
constexpr uint8_t kTftSckPin = 18;
constexpr uint8_t kTftMosiPin = 23;
constexpr uint8_t kTftRstPin = 25;
constexpr uint8_t kTftDcPin = 26;
constexpr uint8_t kTftCsPin = 27;
constexpr uint8_t kTftBlPin = 32;
