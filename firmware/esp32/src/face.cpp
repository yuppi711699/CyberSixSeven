#include "face.h"

#include "ssd1306.h"

namespace {

enum class FaceMode : uint8_t { Idle, Happy, Encourage };

constexpr int16_t kLeftCx = 38;
constexpr int16_t kRightCx = 90;
constexpr int16_t kEyeCy = 30;
constexpr int16_t kEyeR = 18;
constexpr int16_t kPupilR = 7;
constexpr uint32_t kFrameMs = 50;
constexpr uint32_t kBlinkMs = 140;
constexpr uint32_t kLookHoldMinMs = 900;
constexpr uint32_t kLookHoldMaxMs = 2200;

FaceMode mode = FaceMode::Idle;
uint32_t modeStartedAtMs = 0;
uint32_t modeDurationMs = 0;
uint32_t lastFrameAtMs = 0;
uint32_t nextBlinkAtMs = 800;
uint32_t blinkStartedAtMs = 0;
bool blinking = false;
int8_t lookX = 0;
int8_t lookY = 0;
int8_t lookTargetX = 0;
int8_t lookTargetY = 0;
uint32_t lookStartedAtMs = 0;
uint32_t nextLookAtMs = 400;

int16_t clamp16(int16_t v, int16_t lo, int16_t hi) {
  if (v < lo) {
    return lo;
  }
  if (v > hi) {
    return hi;
  }
  return v;
}

int16_t lerpToward(int16_t from, int16_t to, uint32_t elapsedMs, uint32_t spanMs) {
  if (elapsedMs >= spanMs) {
    return to;
  }
  const int32_t d = static_cast<int32_t>(to - from) * static_cast<int32_t>(elapsedMs)
                    / static_cast<int32_t>(spanMs);
  return static_cast<int16_t>(from + d);
}

uint32_t holdMs() {
  return kLookHoldMinMs + (static_cast<uint32_t>(random(0, 1 + (kLookHoldMaxMs - kLookHoldMinMs))));
}

void sparkle(int16_t x, int16_t y, uint32_t now) {
  if (((now / 80) & 1u) == 0) {
    return;
  }
  ssd1306SetPixel(x, y, true);
  ssd1306SetPixel(x - 1, y, true);
  ssd1306SetPixel(x + 1, y, true);
  ssd1306SetPixel(x, y - 1, true);
  ssd1306SetPixel(x, y + 1, true);
}

void drawSmile(int16_t cy) {
  // Shallow U under the eyes. Pixel arc, no trig.
  const int16_t mid = 64;
  for (int16_t x = -18; x <= 18; ++x) {
    const int16_t y = static_cast<int16_t>(cy + (x * x) / 48);
    ssd1306SetPixel(mid + x, y, true);
    ssd1306SetPixel(mid + x, y + 1, true);
  }
}

void drawOpenEye(int16_t cx, int16_t cy, int8_t pupilX, int8_t pupilY, int16_t lidClose) {
  ssd1306FillCircle(cx, cy, kEyeR, true);
  const int16_t px = clamp16(cx + pupilX, cx - 6, cx + 6);
  const int16_t py = clamp16(cy + pupilY, cy - 4, cy + 4);
  ssd1306FillCircle(px, py, kPupilR, false);
  ssd1306FillCircle(px - 3, py - 3, 2, true);
  if (lidClose > 0) {
    const int16_t h = clamp16(lidClose, 0, kEyeR * 2);
    ssd1306FillRect(cx - kEyeR, cy - kEyeR, kEyeR * 2 + 1, h, false);
  }
}

void drawHappyEye(int16_t cx, int16_t cy) {
  // Bottom crescent: white disc minus a disc shifted up. Reads as a smile-squint.
  ssd1306FillCircle(cx, cy, kEyeR - 2, true);
  ssd1306FillCircle(cx, cy - 9, kEyeR - 2, false);
}

void drawEncourageEye(int16_t cx, int16_t cy, int8_t pupilX, int8_t pupilY, int16_t lidClose) {
  // Slightly smaller, hopeful — not an X, not a frown.
  ssd1306FillCircle(cx, cy, kEyeR - 2, true);
  const int16_t px = clamp16(cx + pupilX, cx - 5, cx + 5);
  const int16_t py = clamp16(cy + pupilY - 2, cy - 5, cy + 3);
  ssd1306FillCircle(px, py, kPupilR - 1, false);
  ssd1306FillCircle(px - 2, py - 3, 2, true);
  if (lidClose > 0) {
    ssd1306FillRect(cx - kEyeR, cy - kEyeR, kEyeR * 2 + 1, lidClose, false);
  }
}

void pickLook(uint32_t now) {
  lookStartedAtMs = now;
  lookX = lookTargetX;
  lookY = lookTargetY;
  lookTargetX = static_cast<int8_t>(random(-6, 7));
  lookTargetY = static_cast<int8_t>(random(-3, 4));
  nextLookAtMs = now + holdMs();
}

int16_t currentLook(int8_t from, int8_t to, uint32_t now) {
  return lerpToward(from, to, now - lookStartedAtMs, 180);
}

int16_t blinkLid(uint32_t now) {
  if (!blinking) {
    return 0;
  }
  const uint32_t t = now - blinkStartedAtMs;
  if (t >= kBlinkMs) {
    blinking = false;
    nextBlinkAtMs = now + 1800 + static_cast<uint32_t>(random(0, 1800));
    return 0;
  }
  if (t < kBlinkMs / 2) {
    return static_cast<int16_t>((t * (kEyeR * 2)) / (kBlinkMs / 2));
  }
  const uint32_t back = kBlinkMs - t;
  return static_cast<int16_t>((back * (kEyeR * 2)) / (kBlinkMs / 2));
}

void startBlink(uint32_t now) {
  blinking = true;
  blinkStartedAtMs = now;
}

void drawIdle(uint32_t now, bool mqttReady) {
  if (now >= nextLookAtMs) {
    pickLook(now);
  }
  if (!blinking && now >= nextBlinkAtMs) {
    startBlink(now);
  }
  const int16_t lx = currentLook(lookX, lookTargetX, now);
  const int16_t ly = currentLook(lookY, lookTargetY, now);
  int16_t lid = blinkLid(now);
  if (!mqttReady && lid < 10) {
    lid = 10;  // half-awake while linking
  }
  drawOpenEye(kLeftCx, kEyeCy, static_cast<int8_t>(lx), static_cast<int8_t>(ly), lid);
  drawOpenEye(kRightCx, kEyeCy, static_cast<int8_t>(lx), static_cast<int8_t>(ly), lid);
}

void drawHappy(uint32_t now) {
  const uint32_t t = now - modeStartedAtMs;
  const int16_t hop = static_cast<int16_t>(t % 360);
  const int16_t bounce = static_cast<int16_t>(hop < 180 ? (180 - hop) / 18 : (hop - 180) / 18);
  const int16_t cy = static_cast<int16_t>(kEyeCy - bounce);
  drawHappyEye(kLeftCx, cy);
  drawHappyEye(kRightCx, cy);
  drawSmile(static_cast<int16_t>(52 - bounce / 2));
  sparkle(18, 8, now);
  sparkle(110, 10, now);
  sparkle(64, 6, now);
}

void drawEncourage(uint32_t now) {
  const uint32_t t = now - modeStartedAtMs;
  const int16_t sway = static_cast<int16_t>(((t / 120) % 10) < 5 ? -3 : 3);
  if (!blinking && now >= nextBlinkAtMs) {
    startBlink(now);
  }
  const int16_t lid = blinkLid(now);
  drawEncourageEye(static_cast<int16_t>(kLeftCx + sway), kEyeCy, 0, -2, lid);
  drawEncourageEye(static_cast<int16_t>(kRightCx + sway), kEyeCy, 0, -2, lid);
}

}  // namespace

void faceBegin() {
  if (!ssd1306Begin()) {
    Serial.println("[oled] not found on 0x3C/0x3D — check VCC/GND/SDA=21/SCL=22");
    return;
  }
  Serial.printf("[oled] ssd1306 128x64 addr=0x%02X sda=%u scl=%u\n",
                static_cast<unsigned>(ssd1306Address()),
                static_cast<unsigned>(kOledSdaPin),
                static_cast<unsigned>(kOledSclPin));
  const uint32_t now = millis();
  nextBlinkAtMs = now + 600;
  nextLookAtMs = now + 200;
  lastFrameAtMs = 0;
  // Prove the panel immediately. MQTT happy-face is the same clip.
  facePlayHappy(2);
}

void facePlayHappy(uint8_t intensity) {
  if (!ssd1306Ready()) {
    return;
  }
  mode = FaceMode::Happy;
  modeStartedAtMs = millis();
  modeDurationMs = 900u + 280u * intensity;
  blinking = false;
}

void facePlayEncourage(uint8_t intensity) {
  if (!ssd1306Ready()) {
    return;
  }
  mode = FaceMode::Encourage;
  modeStartedAtMs = millis();
  modeDurationMs = 800u + 220u * intensity;
  blinking = false;
  nextBlinkAtMs = modeStartedAtMs + 180;
}

void faceUpdate(uint32_t now, bool mqttReady) {
  if (!ssd1306Ready()) {
    return;
  }
  if (lastFrameAtMs != 0 && now - lastFrameAtMs < kFrameMs) {
    return;
  }
  lastFrameAtMs = now;

  if (mode != FaceMode::Idle && now - modeStartedAtMs >= modeDurationMs) {
    mode = FaceMode::Idle;
    nextBlinkAtMs = now + 400;
    nextLookAtMs = now + 200;
  }

  ssd1306Clear();
  switch (mode) {
    case FaceMode::Happy:
      drawHappy(now);
      break;
    case FaceMode::Encourage:
      drawEncourage(now);
      break;
    case FaceMode::Idle:
    default:
      drawIdle(now, mqttReady);
      break;
  }
  ssd1306Display();
}
