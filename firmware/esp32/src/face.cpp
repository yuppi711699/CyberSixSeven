#include "face.h"

#include "lcd_type.h"

#if C67_LCD_TYPE == C67_LCD_LCD1602
#include "lcd1602.h"
#elif C67_LCD_TYPE == C67_LCD_ST7789
#include "st7789.h"
#else
#include "ssd1306.h"
#endif

namespace {

enum class FaceMode : uint8_t { Idle, Happy, Encourage };

FaceMode mode = FaceMode::Idle;
uint32_t modeStartedAtMs = 0;
uint32_t modeDurationMs = 0;
uint32_t lastFrameAtMs = 0;
char profileName[17] = "Bob";

#if C67_LCD_TYPE == C67_LCD_LCD1602

constexpr uint32_t kFrameMs = 400;

bool displayReady() {
  return lcd1602Ready();
}

void showGreeting() {
  char line0[17];
  char line1[17];
  // 16x2 cannot fit the full sentence. Wrap so glyphs are actually visible.
  snprintf(line0, sizeof(line0), "Hi, beautiful");
  snprintf(line1, sizeof(line1), "My name is %.5s", profileName);
  lcd1602SetRgb(255, 255, 255);
  lcd1602SetLines(line0, line1);
}

void showIdle(uint32_t now, bool mqttReady) {
  (void)now;
  (void)mqttReady;
  showGreeting();
}

void showHappy() {
  lcd1602SetRgb(0, 180, 40);
  lcd1602SetLines(":)  NICE WORK!", "you got it");
}

void showEncourage() {
  // Soft blue, never red — product rule.
  lcd1602SetRgb(40, 120, 255);
  lcd1602SetLines("almost there", "try once more");
}

#elif C67_LCD_TYPE == C67_LCD_ST7789

constexpr int16_t kLeftCx = 70;
constexpr int16_t kRightCx = 170;
constexpr int16_t kEyeCy = 78;
constexpr int16_t kEyeR = 34;
constexpr int16_t kPupilR = 12;
constexpr int16_t kTextScale = 2;
constexpr int16_t kTextY0 = 168;
constexpr int16_t kTextY1 = 198;
constexpr uint32_t kFrameMs = 80;
// Fixed rects. The eye moves inside the rect; the rect itself never moves,
// so a sprite blit covers the previous frame and leaves no ghosts.
constexpr int16_t kBoxW = 88;
constexpr int16_t kBoxH = 106;
constexpr int16_t kBoxY = 12;
constexpr int16_t kLeftX = 26;
constexpr int16_t kRightX = 126;
constexpr int16_t kSmileX = 72;
constexpr int16_t kSmileY = 114;
constexpr int16_t kSmileW = 96;
constexpr int16_t kSmileH = 34;
constexpr uint32_t kBlinkMs = 140;
constexpr uint32_t kLookHoldMinMs = 900;
constexpr uint32_t kLookHoldMaxMs = 2200;

// rgb(8, 12, 24), rgb(0, 180, 40), rgb(40, 120, 255). Never red.
constexpr uint16_t kBg = 0x0863;
constexpr uint16_t kSclera = 0xFFFF;
constexpr uint16_t kPupil = 0x0000;
constexpr uint16_t kHappyText = 0x05A5;
constexpr uint16_t kEncourageText = 0x2BDF;
constexpr uint16_t kSparkle = 0xFEE7;

uint32_t nextBlinkAtMs = 800;
uint32_t blinkStartedAtMs = 0;
bool blinking = false;
int8_t lookX = 0;
int8_t lookY = 0;
int8_t lookTargetX = 0;
int8_t lookTargetY = 0;
uint32_t lookStartedAtMs = 0;
uint32_t nextLookAtMs = 400;
bool textDirty = true;

alignas(4) uint16_t eyeBox[kBoxW * kBoxH];
alignas(4) uint16_t smileBox[kSmileW * kSmileH];

bool displayReady() {
  return st7789Ready();
}

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

int boxIsqrt(int n) {
  if (n <= 0) {
    return 0;
  }
  int x = n;
  for (;;) {
    const int y = (x + n / x) / 2;
    if (y >= x) {
      return x;
    }
    x = y;
  }
}

void boxClear(uint16_t* box, int16_t w, int16_t h) {
  const size_t n = static_cast<size_t>(w) * static_cast<size_t>(h);
  for (size_t i = 0; i < n; ++i) {
    box[i] = kBg;
  }
}

void boxHLine(uint16_t* box, int16_t bw, int16_t bh, int16_t x, int16_t y, int16_t w, uint16_t color) {
  if (y < 0 || y >= bh || w <= 0) {
    return;
  }
  if (x < 0) {
    w = static_cast<int16_t>(w + x);
    x = 0;
  }
  if (x >= bw || w <= 0) {
    return;
  }
  if (x + w > bw) {
    w = static_cast<int16_t>(bw - x);
  }
  uint16_t* row = box + static_cast<size_t>(y) * static_cast<size_t>(bw) + static_cast<size_t>(x);
  for (int16_t i = 0; i < w; ++i) {
    row[i] = color;
  }
}

void boxFillRect(uint16_t* box, int16_t bw, int16_t bh, int16_t x, int16_t y, int16_t w, int16_t h,
                 uint16_t color) {
  for (int16_t row = 0; row < h; ++row) {
    boxHLine(box, bw, bh, x, static_cast<int16_t>(y + row), w, color);
  }
}

void boxFillCircle(uint16_t* box, int16_t bw, int16_t bh, int16_t cx, int16_t cy, int16_t r,
                   uint16_t color) {
  if (r < 0) {
    return;
  }
  const int r2 = static_cast<int>(r) * static_cast<int>(r);
  for (int16_t dy = static_cast<int16_t>(-r); dy <= r; ++dy) {
    const int16_t dx = static_cast<int16_t>(boxIsqrt(r2 - static_cast<int>(dy) * dy));
    boxHLine(box, bw, bh, static_cast<int16_t>(cx - dx), static_cast<int16_t>(cy + dy),
             static_cast<int16_t>(2 * dx + 1), color);
  }
}

void flushEye(int16_t boxX) {
  st7789Blit(boxX, kBoxY, kBoxW, kBoxH, eyeBox);
}

void drawCentered(int16_t y, const char* text, uint16_t color) {
  const int16_t width = st7789TextWidth(text, static_cast<uint8_t>(kTextScale));
  int16_t x = static_cast<int16_t>((kTftWidth - width) / 2);
  if (x < 0) {
    x = 0;
  }
  st7789DrawText(x, y, text, color, static_cast<uint8_t>(kTextScale));
}

void paintText() {
  st7789FillRect(0, 162, kTftWidth, 74, kBg);
  char line0[24];
  char line1[24];
  uint16_t color = kSclera;
  switch (mode) {
    case FaceMode::Happy:
      snprintf(line0, sizeof(line0), ":)  NICE WORK!");
      snprintf(line1, sizeof(line1), "you got it");
      color = kHappyText;
      break;
    case FaceMode::Encourage:
      snprintf(line0, sizeof(line0), "almost there");
      snprintf(line1, sizeof(line1), "try once more");
      color = kEncourageText;
      break;
    case FaceMode::Idle:
    default:
      snprintf(line0, sizeof(line0), "Hi, beautiful");
      snprintf(line1, sizeof(line1), "My name is %.9s", profileName);
      color = kSclera;
      break;
  }
  drawCentered(kTextY0, line0, color);
  drawCentered(kTextY1, line1, color);
}

void sparkle(int16_t x, int16_t y, uint32_t now) {
  const bool on = ((now / 80) & 1u) != 0;
  const uint16_t color = on ? kSparkle : kBg;
  st7789FillRect(static_cast<int16_t>(x - 4), y, 9, 2, color);
  st7789FillRect(static_cast<int16_t>(x - 1), static_cast<int16_t>(y - 3), 3, 8, color);
}

void drawSmile(int16_t cy) {
  boxClear(smileBox, kSmileW, kSmileH);
  constexpr int16_t mid = 120;
  for (int16_t x = -40; x <= 40; x = static_cast<int16_t>(x + 2)) {
    const int16_t sy = static_cast<int16_t>(cy + (x * x) / 110);
    boxFillRect(smileBox, kSmileW, kSmileH, static_cast<int16_t>(mid + x - kSmileX),
                static_cast<int16_t>(sy - kSmileY), 3, 3, kSclera);
  }
  st7789Blit(kSmileX, kSmileY, kSmileW, kSmileH, smileBox);
}

void drawOpenEye(int16_t boxX, int16_t screenCx, int16_t screenCy, int8_t pupilX, int8_t pupilY,
                 int16_t lidClose) {
  boxClear(eyeBox, kBoxW, kBoxH);
  const int16_t cx = static_cast<int16_t>(screenCx - boxX);
  const int16_t cy = static_cast<int16_t>(screenCy - kBoxY);
  boxFillCircle(eyeBox, kBoxW, kBoxH, cx, cy, kEyeR, kSclera);
  const int16_t px = clamp16(static_cast<int16_t>(cx + pupilX), static_cast<int16_t>(cx - 10),
                             static_cast<int16_t>(cx + 10));
  const int16_t py = clamp16(static_cast<int16_t>(cy + pupilY), static_cast<int16_t>(cy - 6),
                             static_cast<int16_t>(cy + 6));
  boxFillCircle(eyeBox, kBoxW, kBoxH, px, py, kPupilR, kPupil);
  boxFillCircle(eyeBox, kBoxW, kBoxH, static_cast<int16_t>(px - 5), static_cast<int16_t>(py - 5), 3,
                kSclera);
  if (lidClose > 0) {
    const int16_t h = clamp16(lidClose, 0, static_cast<int16_t>(kEyeR * 2));
    boxFillRect(eyeBox, kBoxW, kBoxH, static_cast<int16_t>(cx - kEyeR),
                static_cast<int16_t>(cy - kEyeR), static_cast<int16_t>(kEyeR * 2 + 1), h, kBg);
  }
  flushEye(boxX);
}

void drawHappyEye(int16_t boxX, int16_t screenCx, int16_t screenCy) {
  boxClear(eyeBox, kBoxW, kBoxH);
  const int16_t cx = static_cast<int16_t>(screenCx - boxX);
  const int16_t cy = static_cast<int16_t>(screenCy - kBoxY);
  const int16_t r = static_cast<int16_t>(kEyeR - 4);
  boxFillCircle(eyeBox, kBoxW, kBoxH, cx, cy, r, kSclera);
  boxFillCircle(eyeBox, kBoxW, kBoxH, cx, static_cast<int16_t>(cy - 16), r, kBg);
  flushEye(boxX);
}

void drawEncourageEye(int16_t boxX, int16_t screenCx, int16_t screenCy, int8_t pupilX, int8_t pupilY,
                      int16_t lidClose) {
  boxClear(eyeBox, kBoxW, kBoxH);
  const int16_t cx = static_cast<int16_t>(screenCx - boxX);
  const int16_t cy = static_cast<int16_t>(screenCy - kBoxY);
  boxFillCircle(eyeBox, kBoxW, kBoxH, cx, cy, static_cast<int16_t>(kEyeR - 4), kSclera);
  const int16_t px = clamp16(static_cast<int16_t>(cx + pupilX), static_cast<int16_t>(cx - 8),
                             static_cast<int16_t>(cx + 8));
  const int16_t py = clamp16(static_cast<int16_t>(cy + pupilY - 4), static_cast<int16_t>(cy - 8),
                             static_cast<int16_t>(cy + 4));
  boxFillCircle(eyeBox, kBoxW, kBoxH, px, py, static_cast<int16_t>(kPupilR - 2), kPupil);
  boxFillCircle(eyeBox, kBoxW, kBoxH, static_cast<int16_t>(px - 4), static_cast<int16_t>(py - 4), 2,
                kSclera);
  if (lidClose > 0) {
    boxFillRect(eyeBox, kBoxW, kBoxH, static_cast<int16_t>(cx - kEyeR),
                static_cast<int16_t>(cy - kEyeR), static_cast<int16_t>(kEyeR * 2 + 1), lidClose, kBg);
  }
  flushEye(boxX);
}

void pickLook(uint32_t now) {
  lookStartedAtMs = now;
  lookX = lookTargetX;
  lookY = lookTargetY;
  lookTargetX = static_cast<int8_t>(random(-10, 11));
  lookTargetY = static_cast<int8_t>(random(-5, 6));
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
  if (!mqttReady && lid < 20) {
    lid = 20;
  }
  drawOpenEye(kLeftX, kLeftCx, kEyeCy, static_cast<int8_t>(lx), static_cast<int8_t>(ly), lid);
  drawOpenEye(kRightX, kRightCx, kEyeCy, static_cast<int8_t>(lx), static_cast<int8_t>(ly), lid);
}

void drawHappy(uint32_t now) {
  const uint32_t t = now - modeStartedAtMs;
  const int16_t hop = static_cast<int16_t>(t % 360);
  const int16_t bounce = static_cast<int16_t>(hop < 180 ? (180 - hop) / 12 : (hop - 180) / 12);
  const int16_t cy = static_cast<int16_t>(kEyeCy - bounce);
  drawHappyEye(kLeftX, kLeftCx, cy);
  drawHappyEye(kRightX, kRightCx, cy);
  drawSmile(static_cast<int16_t>(124 - bounce / 2));
  sparkle(36, 14, now);
  sparkle(204, 16, now);
  sparkle(120, 10, now);
}

void drawEncourage(uint32_t now) {
  const uint32_t t = now - modeStartedAtMs;
  const int16_t sway = static_cast<int16_t>(((t / 120) % 10) < 5 ? -6 : 6);
  if (!blinking && now >= nextBlinkAtMs) {
    startBlink(now);
  }
  const int16_t lid = blinkLid(now);
  drawEncourageEye(kLeftX, static_cast<int16_t>(kLeftCx + sway), kEyeCy, 0, -2, lid);
  drawEncourageEye(kRightX, static_cast<int16_t>(kRightCx + sway), kEyeCy, 0, -2, lid);
}

#else

constexpr int16_t kLeftCx = 38;
constexpr int16_t kRightCx = 90;
constexpr int16_t kEyeCy = 30;
constexpr int16_t kEyeR = 18;
constexpr int16_t kPupilR = 7;
constexpr uint32_t kFrameMs = 50;
constexpr uint32_t kBlinkMs = 140;
constexpr uint32_t kLookHoldMinMs = 900;
constexpr uint32_t kLookHoldMaxMs = 2200;

uint32_t nextBlinkAtMs = 800;
uint32_t blinkStartedAtMs = 0;
bool blinking = false;
int8_t lookX = 0;
int8_t lookY = 0;
int8_t lookTargetX = 0;
int8_t lookTargetY = 0;
uint32_t lookStartedAtMs = 0;
uint32_t nextLookAtMs = 400;

bool displayReady() {
  return ssd1306Ready();
}

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
  ssd1306FillCircle(cx, cy, kEyeR - 2, true);
  ssd1306FillCircle(cx, cy - 9, kEyeR - 2, false);
}

void drawEncourageEye(int16_t cx, int16_t cy, int8_t pupilX, int8_t pupilY, int16_t lidClose) {
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
    lid = 10;
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

#endif

}  // namespace

const char* faceLcdName() {
#if C67_LCD_TYPE == C67_LCD_LCD1602
  return "lcd1602";
#elif C67_LCD_TYPE == C67_LCD_ST7789
  return "st7789";
#else
  return "ssd1306";
#endif
}

void faceSetProfileName(const char* name) {
  if (name == nullptr || name[0] == '\0') {
    snprintf(profileName, sizeof(profileName), "Bob");
  } else {
    snprintf(profileName, sizeof(profileName), "%s", name);
  }
#if C67_LCD_TYPE == C67_LCD_ST7789
  textDirty = true;
#endif
}

void faceBegin() {
#if C67_LCD_TYPE == C67_LCD_LCD1602
  if (!lcd1602Begin()) {
    return;
  }
  Serial.printf("[lcd] %s addr=0x%02X rgb=0x%02X sda=%u scl=%u\n",
                lcd1602Kind(),
                static_cast<unsigned>(lcd1602Address()),
                static_cast<unsigned>(lcd1602RgbAddress()),
                static_cast<unsigned>(kLcdSdaPin),
                static_cast<unsigned>(kLcdSclPin));
  Serial.printf("[lcd] Hi, beautiful student. My name is %s\n", profileName);
  lastFrameAtMs = 0;
  showGreeting();
#elif C67_LCD_TYPE == C67_LCD_ST7789
  if (!st7789Begin()) {
    return;
  }
  Serial.printf("[lcd] st7789 240x240 sck=%u mosi=%u dc=%u cs=%u rst=%u blk=%u row=%u col=%u madctl=0x%02X\n",
                static_cast<unsigned>(kTftSckPin),
                static_cast<unsigned>(kTftMosiPin),
                static_cast<unsigned>(kTftDcPin),
                static_cast<unsigned>(kTftCsPin),
                static_cast<unsigned>(kTftRstPin),
                static_cast<unsigned>(kTftBlPin),
                static_cast<unsigned>(C67_ST7789_ROW_START),
                static_cast<unsigned>(C67_ST7789_COL_START),
                static_cast<unsigned>(C67_ST7789_MADCTL));
  Serial.printf("[lcd] Hi, beautiful student. My name is %s\n", profileName);
  st7789FillScreen(kBg);
  const uint32_t now = millis();
  nextBlinkAtMs = now + 600;
  nextLookAtMs = now + 200;
  lastFrameAtMs = 0;
  textDirty = true;
  facePlayHappy(2);
#else
  if (!ssd1306Begin()) {
    Serial.println("[oled] not found on 0x3C/0x3D — check VCC/GND/SDA=21/SCL=22");
    return;
  }
  Serial.printf("[oled] ssd1306 128x64 addr=0x%02X sda=%u scl=%u\n",
                static_cast<unsigned>(ssd1306Address()),
                static_cast<unsigned>(kLcdSdaPin),
                static_cast<unsigned>(kLcdSclPin));
  const uint32_t now = millis();
  nextBlinkAtMs = now + 600;
  nextLookAtMs = now + 200;
  lastFrameAtMs = 0;
  facePlayHappy(2);
#endif
}

void facePlayHappy(uint8_t intensity) {
  if (!displayReady()) {
    return;
  }
  mode = FaceMode::Happy;
  modeStartedAtMs = millis();
  modeDurationMs = 900u + 280u * intensity;
  lastFrameAtMs = 0;
#if C67_LCD_TYPE != C67_LCD_LCD1602
  blinking = false;
#endif
#if C67_LCD_TYPE == C67_LCD_ST7789
  textDirty = true;
#endif
}

void facePlayEncourage(uint8_t intensity) {
  if (!displayReady()) {
    return;
  }
  mode = FaceMode::Encourage;
  modeStartedAtMs = millis();
  modeDurationMs = 800u + 220u * intensity;
  lastFrameAtMs = 0;
#if C67_LCD_TYPE != C67_LCD_LCD1602
  blinking = false;
  nextBlinkAtMs = modeStartedAtMs + 180;
#endif
#if C67_LCD_TYPE == C67_LCD_ST7789
  textDirty = true;
#endif
}

void faceUpdate(uint32_t now, bool mqttReady) {
  if (!displayReady()) {
    return;
  }
  if (mode != FaceMode::Idle && now - modeStartedAtMs >= modeDurationMs) {
    mode = FaceMode::Idle;
    lastFrameAtMs = 0;
#if C67_LCD_TYPE != C67_LCD_LCD1602
    nextBlinkAtMs = now + 400;
    nextLookAtMs = now + 200;
#endif
#if C67_LCD_TYPE == C67_LCD_ST7789
    textDirty = true;
#endif
  }
  if (lastFrameAtMs != 0 && now - lastFrameAtMs < kFrameMs) {
    return;
  }
  lastFrameAtMs = now;

#if C67_LCD_TYPE == C67_LCD_LCD1602
  switch (mode) {
    case FaceMode::Happy:
      showHappy();
      break;
    case FaceMode::Encourage:
      showEncourage();
      break;
    case FaceMode::Idle:
    default:
      showIdle(now, mqttReady);
      break;
  }
#elif C67_LCD_TYPE == C67_LCD_ST7789
  if (textDirty) {
    // Once per mode change. The eye sprites repaint the same frame, so this
    // does not flash. Skipping it leaves the previous smile and sparkles up.
    st7789FillRect(0, 0, kTftWidth, 20, kBg);
    st7789FillRect(kSmileX, kSmileY, kSmileW, kSmileH, kBg);
    paintText();
    textDirty = false;
  }
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
#else
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
#endif
}
