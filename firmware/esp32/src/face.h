#pragma once

#include <Arduino.h>

// Companion-device eyes on the SSD1306. All timing is millis()-based; never
// delay() — a blocking frame stall would drop MQTT keepalive.

void faceBegin();
void facePlayHappy(uint8_t intensity);
void facePlayEncourage(uint8_t intensity);
void faceUpdate(uint32_t now, bool mqttReady);
