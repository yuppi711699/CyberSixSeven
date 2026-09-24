#pragma once

#include <Arduino.h>

// 16x2 I2C. PCF8574A (0x38-0x3F, default 0x3F) is probed before Gravity.
// Gravity DFR0464 only if 0x3E AND an RGB chip (0x60/0x6B/0x2D) both ACK —
// 0x3E alone is PCF8574A, not Gravity.
// Wire only. No LiquidCrystal_I2C, no DFRobot_RGBLCD1602.

bool lcd1602Begin();
bool lcd1602Ready();
uint8_t lcd1602Address();
uint8_t lcd1602RgbAddress();
const char* lcd1602Kind();
void lcd1602SetLines(const char* line0, const char* line1);
void lcd1602SetRgb(uint8_t r, uint8_t g, uint8_t b);
