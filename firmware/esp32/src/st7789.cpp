#include "st7789.h"

#include <SPI.h>

namespace {

// 20 MHz survives Dupont jumpers. 40 MHz speckle or drops pixels on this module.
constexpr uint32_t kSpiHz = 20000000;

// 5x7 ASCII, columns, bit 0 = top. Printable 0x20..0x7E. Classic public-domain
// bitmap (one column per byte) — not an Adafruit GFX font file.
constexpr uint8_t kFont[] = {
    0x00, 0x00, 0x00, 0x00, 0x00,  // 32 space
    0x00, 0x00, 0x5F, 0x00, 0x00,  // 33 !
    0x00, 0x07, 0x00, 0x07, 0x00,  // 34 "
    0x14, 0x7F, 0x14, 0x7F, 0x14,  // 35 #
    0x24, 0x2A, 0x7F, 0x2A, 0x12,  // 36 $
    0x23, 0x13, 0x08, 0x64, 0x62,  // 37 %
    0x36, 0x49, 0x55, 0x22, 0x50,  // 38 &
    0x00, 0x05, 0x03, 0x00, 0x00,  // 39 '
    0x00, 0x1C, 0x22, 0x41, 0x00,  // 40 (
    0x00, 0x41, 0x22, 0x1C, 0x00,  // 41 )
    0x14, 0x08, 0x3E, 0x08, 0x14,  // 42 *
    0x08, 0x08, 0x3E, 0x08, 0x08,  // 43 +
    0x00, 0x50, 0x30, 0x00, 0x00,  // 44 ,
    0x08, 0x08, 0x08, 0x08, 0x08,  // 45 -
    0x00, 0x60, 0x60, 0x00, 0x00,  // 46 .
    0x20, 0x10, 0x08, 0x04, 0x02,  // 47 /
    0x3E, 0x51, 0x49, 0x45, 0x3E,  // 48 0
    0x00, 0x42, 0x7F, 0x40, 0x00,  // 49 1
    0x42, 0x61, 0x51, 0x49, 0x46,  // 50 2
    0x21, 0x41, 0x45, 0x4B, 0x31,  // 51 3
    0x18, 0x14, 0x12, 0x7F, 0x10,  // 52 4
    0x27, 0x45, 0x45, 0x45, 0x39,  // 53 5
    0x3C, 0x4A, 0x49, 0x49, 0x30,  // 54 6
    0x01, 0x71, 0x09, 0x05, 0x03,  // 55 7
    0x36, 0x49, 0x49, 0x49, 0x36,  // 56 8
    0x06, 0x49, 0x49, 0x29, 0x1E,  // 57 9
    0x00, 0x36, 0x36, 0x00, 0x00,  // 58 :
    0x00, 0x56, 0x36, 0x00, 0x00,  // 59 ;
    0x08, 0x14, 0x22, 0x41, 0x00,  // 60 <
    0x14, 0x14, 0x14, 0x14, 0x14,  // 61 =
    0x00, 0x41, 0x22, 0x14, 0x08,  // 62 >
    0x02, 0x01, 0x51, 0x09, 0x06,  // 63 ?
    0x32, 0x49, 0x79, 0x41, 0x3E,  // 64 @
    0x7E, 0x11, 0x11, 0x11, 0x7E,  // 65 A
    0x7F, 0x49, 0x49, 0x49, 0x36,  // 66 B
    0x3E, 0x41, 0x41, 0x41, 0x22,  // 67 C
    0x7F, 0x41, 0x41, 0x22, 0x1C,  // 68 D
    0x7F, 0x49, 0x49, 0x49, 0x41,  // 69 E
    0x7F, 0x09, 0x09, 0x09, 0x01,  // 70 F
    0x3E, 0x41, 0x49, 0x49, 0x7A,  // 71 G
    0x7F, 0x08, 0x08, 0x08, 0x7F,  // 72 H
    0x00, 0x41, 0x7F, 0x41, 0x00,  // 73 I
    0x20, 0x40, 0x41, 0x3F, 0x01,  // 74 J
    0x7F, 0x08, 0x14, 0x22, 0x41,  // 75 K
    0x7F, 0x40, 0x40, 0x40, 0x40,  // 76 L
    0x7F, 0x02, 0x0C, 0x02, 0x7F,  // 77 M
    0x7F, 0x04, 0x08, 0x10, 0x7F,  // 78 N
    0x3E, 0x41, 0x41, 0x41, 0x3E,  // 79 O
    0x7F, 0x09, 0x09, 0x09, 0x06,  // 80 P
    0x3E, 0x41, 0x51, 0x21, 0x5E,  // 81 Q
    0x7F, 0x09, 0x19, 0x29, 0x46,  // 82 R
    0x46, 0x49, 0x49, 0x49, 0x31,  // 83 S
    0x01, 0x01, 0x7F, 0x01, 0x01,  // 84 T
    0x3F, 0x40, 0x40, 0x40, 0x3F,  // 85 U
    0x1F, 0x20, 0x40, 0x20, 0x1F,  // 86 V
    0x3F, 0x40, 0x38, 0x40, 0x3F,  // 87 W
    0x63, 0x14, 0x08, 0x14, 0x63,  // 88 X
    0x07, 0x08, 0x70, 0x08, 0x07,  // 89 Y
    0x61, 0x51, 0x49, 0x45, 0x43,  // 90 Z
    0x00, 0x7F, 0x41, 0x41, 0x00,  // 91 [
    0x02, 0x04, 0x08, 0x10, 0x20,  // 92 backslash
    0x00, 0x41, 0x41, 0x7F, 0x00,  // 93 ]
    0x04, 0x02, 0x01, 0x02, 0x04,  // 94 ^
    0x40, 0x40, 0x40, 0x40, 0x40,  // 95 _
    0x00, 0x01, 0x02, 0x04, 0x00,  // 96 `
    0x20, 0x54, 0x54, 0x54, 0x78,  // 97 a
    0x7F, 0x48, 0x44, 0x44, 0x38,  // 98 b
    0x38, 0x44, 0x44, 0x44, 0x20,  // 99 c
    0x38, 0x44, 0x44, 0x48, 0x7F,  // 100 d
    0x38, 0x54, 0x54, 0x54, 0x18,  // 101 e
    0x08, 0x7E, 0x09, 0x01, 0x02,  // 102 f
    0x0C, 0x52, 0x52, 0x52, 0x3E,  // 103 g
    0x7F, 0x08, 0x04, 0x04, 0x78,  // 104 h
    0x00, 0x44, 0x7D, 0x40, 0x00,  // 105 i
    0x20, 0x40, 0x44, 0x3D, 0x00,  // 106 j
    0x7F, 0x10, 0x28, 0x44, 0x00,  // 107 k
    0x00, 0x41, 0x7F, 0x40, 0x00,  // 108 l
    0x7C, 0x04, 0x18, 0x04, 0x78,  // 109 m
    0x7C, 0x08, 0x04, 0x04, 0x78,  // 110 n
    0x38, 0x44, 0x44, 0x44, 0x38,  // 111 o
    0x7C, 0x14, 0x14, 0x14, 0x08,  // 112 p
    0x08, 0x14, 0x14, 0x18, 0x7C,  // 113 q
    0x7C, 0x08, 0x04, 0x04, 0x08,  // 114 r
    0x48, 0x54, 0x54, 0x54, 0x20,  // 115 s
    0x04, 0x3F, 0x44, 0x40, 0x20,  // 116 t
    0x3C, 0x40, 0x40, 0x20, 0x7C,  // 117 u
    0x1C, 0x20, 0x40, 0x20, 0x1C,  // 118 v
    0x3C, 0x40, 0x30, 0x40, 0x3C,  // 119 w
    0x44, 0x28, 0x10, 0x28, 0x44,  // 120 x
    0x0C, 0x50, 0x50, 0x50, 0x3C,  // 121 y
    0x44, 0x64, 0x54, 0x4C, 0x44,  // 122 z
    0x00, 0x08, 0x36, 0x41, 0x00,  // 123 {
    0x00, 0x00, 0x7F, 0x00, 0x00,  // 124 |
    0x00, 0x41, 0x36, 0x08, 0x00,  // 125 }
    0x08, 0x08, 0x2A, 0x1C, 0x08,  // 126 ~
};

constexpr size_t kFontChars = (sizeof(kFont) / 5);
static_assert(kFontChars == 95, "5x7 font must cover ASCII 0x20..0x7E");

// ESP32 spiWriteNL loads the buffer as uint32_t. A bool sitting in front of
// this array leaves it at an odd address, the first fill raises a load-store
// fault, and setup() resets — the panel never leaves reset, so the glass stays
// black. +4 covers the extra word the HAL reads when the row length isn't a
// multiple of 4.
alignas(4) uint8_t rowBuf[kTftWidth * 2 + 4];
static_assert(alignof(rowBuf) >= 4, "SPI.writeBytes faults on an unaligned buffer");

bool ready = false;

int isqrt(int n) {
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

struct SpiLock {
  SpiLock() {
    SPI.beginTransaction(SPISettings(kSpiHz, MSBFIRST, SPI_MODE0));
    digitalWrite(kTftCsPin, LOW);
  }
  ~SpiLock() {
    digitalWrite(kTftCsPin, HIGH);
    SPI.endTransaction();
  }
  SpiLock(const SpiLock&) = delete;
  SpiLock& operator=(const SpiLock&) = delete;
};

void writeCmd(uint8_t cmd) {
  digitalWrite(kTftDcPin, LOW);
  SPI.transfer(cmd);
}

void writeData(uint8_t data) {
  digitalWrite(kTftDcPin, HIGH);
  SPI.transfer(data);
}

void command(uint8_t cmd, const uint8_t* data, size_t n) {
  SpiLock lock;
  writeCmd(cmd);
  for (size_t i = 0; i < n; ++i) {
    writeData(data[i]);
  }
}

void command(uint8_t cmd) {
  command(cmd, nullptr, 0);
}

void command1(uint8_t cmd, uint8_t data) {
  command(cmd, &data, 1);
}

void setWindow(int16_t x, int16_t y, int16_t w, int16_t h) {
  const uint16_t x0 = static_cast<uint16_t>(x + C67_ST7789_COL_START);
  const uint16_t y0 = static_cast<uint16_t>(y + C67_ST7789_ROW_START);
  const uint16_t x1 = static_cast<uint16_t>(x0 + static_cast<uint16_t>(w) - 1);
  const uint16_t y1 = static_cast<uint16_t>(y0 + static_cast<uint16_t>(h) - 1);
  writeCmd(0x2A);
  writeData(static_cast<uint8_t>(x0 >> 8));
  writeData(static_cast<uint8_t>(x0));
  writeData(static_cast<uint8_t>(x1 >> 8));
  writeData(static_cast<uint8_t>(x1));
  writeCmd(0x2B);
  writeData(static_cast<uint8_t>(y0 >> 8));
  writeData(static_cast<uint8_t>(y0));
  writeData(static_cast<uint8_t>(y1 >> 8));
  writeData(static_cast<uint8_t>(y1));
  writeCmd(0x2C);
}

const uint8_t* glyph(char c) {
  if (c < 32 || c > 126) {
    c = ' ';
  }
  const size_t index = static_cast<size_t>(c - 32);
  if (index >= kFontChars) {
    return kFont;
  }
  return kFont + index * 5;
}

bool clipRect(int16_t& x, int16_t& y, int16_t& w, int16_t& h) {
  if (w <= 0 || h <= 0) {
    return false;
  }
  if (x < 0) {
    w = static_cast<int16_t>(w + x);
    x = 0;
  }
  if (y < 0) {
    h = static_cast<int16_t>(h + y);
    y = 0;
  }
  if (x >= kTftWidth || y >= kTftHeight || w <= 0 || h <= 0) {
    return false;
  }
  if (x + w > kTftWidth) {
    w = static_cast<int16_t>(kTftWidth - x);
  }
  if (y + h > kTftHeight) {
    h = static_cast<int16_t>(kTftHeight - y);
  }
  return w > 0 && h > 0 && w <= kTftWidth;
}

// Caller holds CS and the SPI transaction. One lock per circle, not per scanline.
void fillRectLocked(int16_t x, int16_t y, int16_t w, int16_t h, uint16_t color) {
  if (!clipRect(x, y, w, h)) {
    return;
  }
  setWindow(x, y, w, h);
  const uint8_t hi = static_cast<uint8_t>(color >> 8);
  const uint8_t lo = static_cast<uint8_t>(color);
  for (int16_t i = 0; i < w; ++i) {
    rowBuf[static_cast<size_t>(i) * 2] = hi;
    rowBuf[static_cast<size_t>(i) * 2 + 1] = lo;
  }
  digitalWrite(kTftDcPin, HIGH);
  const size_t rowBytes = static_cast<size_t>(w) * 2;
  for (int16_t row = 0; row < h; ++row) {
    SPI.writeBytes(rowBuf, rowBytes);
  }
}

}  // namespace

bool st7789Begin() {
  ready = false;
  pinMode(kTftBlPin, OUTPUT);
  digitalWrite(kTftBlPin, HIGH);
  pinMode(kTftCsPin, OUTPUT);
  pinMode(kTftDcPin, OUTPUT);
  pinMode(kTftRstPin, OUTPUT);
  digitalWrite(kTftCsPin, HIGH);
  digitalWrite(kTftDcPin, HIGH);
  digitalWrite(kTftRstPin, HIGH);

  SPI.begin(kTftSckPin, -1, kTftMosiPin, -1);

  digitalWrite(kTftRstPin, LOW);
  delay(20);
  digitalWrite(kTftRstPin, HIGH);
  delay(150);

  command(0x01);
  delay(150);
  command(0x11);
  delay(120);

  command1(0x36, static_cast<uint8_t>(C67_ST7789_MADCTL));
  command1(0x3A, 0x55);

  const uint8_t porch[] = {0x0C, 0x0C, 0x00, 0x33, 0x33};
  command(0xB2, porch, sizeof(porch));
  command1(0xB7, 0x35);
  command1(0xBB, 0x19);
  command1(0xC0, 0x2C);
  command1(0xC2, 0x01);
  command1(0xC3, 0x12);
  command1(0xC4, 0x20);
  command1(0xC6, 0x0F);
  const uint8_t power[] = {0xA4, 0xA1};
  command(0xD0, power, sizeof(power));
  const uint8_t pGamma[] = {0xD0, 0x04, 0x0D, 0x11, 0x13, 0x2B, 0x3F,
                            0x54, 0x4C, 0x18, 0x0D, 0x0B, 0x1F, 0x23};
  const uint8_t nGamma[] = {0xD0, 0x04, 0x0C, 0x11, 0x13, 0x2C, 0x3F,
                            0x44, 0x51, 0x2F, 0x1F, 0x1F, 0x20, 0x23};
  command(0xE0, pGamma, sizeof(pGamma));
  command(0xE1, nGamma, sizeof(nGamma));

  // IPS glass is color-inverted until INVON. Skip this and white eyes are black.
  command(0x21);
  command(0x13);
  command(0x29);
  delay(120);

  ready = true;
  st7789FillScreen(0x0000);
  return true;
}

bool st7789Ready() {
  return ready;
}

void st7789FillRect(int16_t x, int16_t y, int16_t w, int16_t h, uint16_t color) {
  if (!ready) {
    return;
  }
  SpiLock lock;
  fillRectLocked(x, y, w, h, color);
}

void st7789FillScreen(uint16_t color) {
  st7789FillRect(0, 0, kTftWidth, kTftHeight, color);
}

void st7789FillCircle(int16_t cx, int16_t cy, int16_t r, uint16_t color) {
  if (!ready || r < 0) {
    return;
  }
  SpiLock lock;
  const int r2 = static_cast<int>(r) * static_cast<int>(r);
  for (int16_t dy = static_cast<int16_t>(-r); dy <= r; ++dy) {
    const int16_t dx = static_cast<int16_t>(isqrt(r2 - static_cast<int>(dy) * dy));
    fillRectLocked(static_cast<int16_t>(cx - dx), static_cast<int16_t>(cy + dy),
                   static_cast<int16_t>(2 * dx + 1), 1, color);
  }
}

int16_t st7789TextWidth(const char* text, uint8_t scale) {
  if (text == nullptr || text[0] == '\0' || scale == 0) {
    return 0;
  }
  const int16_t advance = static_cast<int16_t>(6 * scale);
  int16_t n = 0;
  while (text[n] != '\0' && n < 64) {
    ++n;
  }
  return static_cast<int16_t>(n * advance);
}

void st7789DrawText(int16_t x, int16_t y, const char* text, uint16_t color, uint8_t scale) {
  if (!ready || text == nullptr || scale == 0 || scale > 4) {
    return;
  }
  const int16_t advance = static_cast<int16_t>(6 * scale);
  while (*text != '\0') {
    const uint8_t* cols = glyph(*text);
    for (uint8_t col = 0; col < 5; ++col) {
      const uint8_t bits = cols[col];
      uint8_t row = 0;
      while (row < 7) {
        if ((bits & static_cast<uint8_t>(1u << row)) == 0) {
          ++row;
          continue;
        }
        const uint8_t start = row;
        while (row < 7 && (bits & static_cast<uint8_t>(1u << row)) != 0) {
          ++row;
        }
        const uint8_t run = static_cast<uint8_t>(row - start);
        st7789FillRect(static_cast<int16_t>(x + col * scale),
                       static_cast<int16_t>(y + start * scale),
                       scale,
                       static_cast<int16_t>(run * scale),
                       color);
      }
    }
    x = static_cast<int16_t>(x + advance);
    ++text;
  }
}
