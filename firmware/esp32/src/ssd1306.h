#pragma once

#include <Arduino.h>

#include "lcd_type.h"

// 0.96" 128x64 SSD1306 over I2C. No Adafruit/U8g2 — Wire is in the Arduino core
// and platformio.ini lib_deps stay ArduinoJson + PubSubClient only.

constexpr uint8_t kOledSdaPin = kLcdSdaPin;
constexpr uint8_t kOledSclPin = kLcdSclPin;
constexpr uint8_t kOledWidth = 128;
constexpr uint8_t kOledHeight = 64;

bool ssd1306Begin();
bool ssd1306Ready();
uint8_t ssd1306Address();
void ssd1306Clear();
void ssd1306SetPixel(int16_t x, int16_t y, bool on);
void ssd1306HLine(int16_t x, int16_t y, int16_t w, bool on);
void ssd1306FillRect(int16_t x, int16_t y, int16_t w, int16_t h, bool on);
void ssd1306FillCircle(int16_t cx, int16_t cy, int16_t r, bool on);
void ssd1306Display();
