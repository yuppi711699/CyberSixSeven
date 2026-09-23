#pragma once

#include <Arduino.h>

// HD44780 16x2 through a PCF8574 / PCF8574A I2C backpack (the 4-pin
// "LCD1602 I2C" module). No LiquidCrystal_I2C — Wire only, same lib_deps.

bool lcd1602Begin();
bool lcd1602Ready();
uint8_t lcd1602Address();
void lcd1602SetLines(const char* line0, const char* line1);
