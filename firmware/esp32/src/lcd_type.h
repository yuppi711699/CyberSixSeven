#pragma once

#include <Arduino.h>

// Selected by the PlatformIO env in platformio.ini:
//   -e esp32dev            → C67_LCD_TYPE=C67_LCD_SSD1306
//   -e esp32dev-lcd1602    → C67_LCD_TYPE=C67_LCD_LCD1602
// Integer macros so #if works. Do not pass a string to -D C67_LCD_TYPE.

#define C67_LCD_SSD1306 1
#define C67_LCD_LCD1602 2
// Next panel: 3. Do not reuse 1 or 2. Add the env + README registry row in the same change.

#ifndef C67_LCD_TYPE
#define C67_LCD_TYPE C67_LCD_SSD1306
#endif

// Both panels share the DevKit I2C pins. 1602 backpacks want 5 V on VCC;
// the SSD1306 module is usually 3.3 V. SDA/SCL stay 3.3 V either way.
constexpr uint8_t kLcdSdaPin = 21;
constexpr uint8_t kLcdSclPin = 22;
