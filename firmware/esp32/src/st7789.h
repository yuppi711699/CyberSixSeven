#pragma once

#include <Arduino.h>

#include "lcd_type.h"

// 1.54" 240x240 IPS, ST7789, 4-wire SPI (DORHEA-style 8-pin, silkscreen V1.1).
// Arduino SPI only — no TFT_eSPI, no Adafruit. lib_deps stay ArduinoJson + PubSubClient.
//
// 8-pin V1.1 glass uses row 0 and MADCTL 0x00. If the picture is shifted up
// with a black band at the bottom, the window is the Adafruit one:
// -D C67_ST7789_ROW_START=80. Upside down: -D C67_ST7789_MADCTL=0xC0.
// Red/blue swapped: -D C67_ST7789_MADCTL=0x08.

constexpr int16_t kTftWidth = 240;
constexpr int16_t kTftHeight = 240;

#ifndef C67_ST7789_COL_START
#define C67_ST7789_COL_START 0
#endif
#ifndef C67_ST7789_ROW_START
#define C67_ST7789_ROW_START 0
#endif
#ifndef C67_ST7789_MADCTL
#define C67_ST7789_MADCTL 0x00
#endif

bool st7789Begin();
bool st7789Ready();
void st7789FillScreen(uint16_t color);
void st7789FillRect(int16_t x, int16_t y, int16_t w, int16_t h, uint16_t color);
void st7789FillCircle(int16_t cx, int16_t cy, int16_t r, uint16_t color);
// One window write. pixels are native RGB565, length w*h. Replaces the rect
// without a separate clear, so a sprite blit does not flash the background.
void st7789Blit(int16_t x, int16_t y, int16_t w, int16_t h, const uint16_t* pixels);
int16_t st7789TextWidth(const char* text, uint8_t scale);
void st7789DrawText(int16_t x, int16_t y, const char* text, uint16_t color, uint8_t scale);
