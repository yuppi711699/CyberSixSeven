#pragma once

#include <Arduino.h>

// Companion-device display. SSD1306 = eyes; LCD1602 = 16x2 text. Timing is
// millis()-based; never delay() — a blocking frame stall would drop MQTT keepalive.

void faceBegin();
void faceSetProfileName(const char* name);
void facePlayHappy(uint8_t intensity);
void facePlayEncourage(uint8_t intensity);
void faceUpdate(uint32_t now, bool mqttReady);
const char* faceLcdName();
