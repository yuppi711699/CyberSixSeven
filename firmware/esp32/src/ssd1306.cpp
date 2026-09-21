#include "ssd1306.h"

#include <Wire.h>
#include <string.h>

namespace {

constexpr size_t kBufferSize = (kOledWidth * kOledHeight) / 8;
// 100 kHz survives Dupont jumpers. 400 kHz ACKs then draws a blank panel.
constexpr uint32_t kI2cHz = 100000;

uint8_t buffer[kBufferSize];
uint8_t address = 0;
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

bool probe(uint8_t addr) {
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

void sendCommand(uint8_t cmd) {
  Wire.beginTransmission(address);
  Wire.write(static_cast<uint8_t>(0x00));
  Wire.write(cmd);
  Wire.endTransmission();
}

void sendCommandList(const uint8_t* cmds, size_t n) {
  for (size_t i = 0; i < n; ++i) {
    sendCommand(cmds[i]);
  }
}

}  // namespace

bool ssd1306Begin() {
  ready = false;
  address = 0;
  Wire.begin(kOledSdaPin, kOledSclPin);
  Wire.setClock(kI2cHz);

  if (probe(0x3C)) {
    address = 0x3C;
  } else if (probe(0x3D)) {
    address = 0x3D;
  } else {
    return false;
  }

  // Charge-pump 128x64. Memory mode 0x02 = page addressing so cheap clones
  // and SH1106 actually show pixels (horizontal 0x21/0x22 windows are ignored).
  const uint8_t init[] = {
      0xAE, 0xD5, 0x80, 0xA8, 0x3F, 0xD3, 0x00, 0x40, 0x8D, 0x14, 0x20, 0x02,
      0xA1, 0xC8, 0xDA, 0x12, 0x81, 0xCF, 0xD9, 0xF1, 0xDB, 0x40, 0xA4, 0xA6,
      0x2E, 0xAF};
  sendCommandList(init, sizeof(init));
  ready = true;
  memset(buffer, 0xFF, sizeof(buffer));
  ssd1306Display();
  delay(300);
  ssd1306Clear();
  ssd1306Display();
  return true;
}

bool ssd1306Ready() {
  return ready;
}

uint8_t ssd1306Address() {
  return address;
}

void ssd1306Clear() {
  memset(buffer, 0, sizeof(buffer));
}

void ssd1306SetPixel(int16_t x, int16_t y, bool on) {
  if (x < 0 || x >= kOledWidth || y < 0 || y >= kOledHeight) {
    return;
  }
  const size_t i = static_cast<size_t>(x) + (static_cast<size_t>(y) / 8) * kOledWidth;
  const uint8_t bit = static_cast<uint8_t>(1u << (y & 7));
  if (on) {
    buffer[i] = static_cast<uint8_t>(buffer[i] | bit);
  } else {
    buffer[i] = static_cast<uint8_t>(buffer[i] & static_cast<uint8_t>(~bit));
  }
}

void ssd1306HLine(int16_t x, int16_t y, int16_t w, bool on) {
  if (w <= 0) {
    return;
  }
  for (int16_t i = 0; i < w; ++i) {
    ssd1306SetPixel(x + i, y, on);
  }
}

void ssd1306FillRect(int16_t x, int16_t y, int16_t w, int16_t h, bool on) {
  for (int16_t row = 0; row < h; ++row) {
    ssd1306HLine(x, y + row, w, on);
  }
}

void ssd1306FillCircle(int16_t cx, int16_t cy, int16_t r, bool on) {
  if (r < 0) {
    return;
  }
  for (int16_t dy = -r; dy <= r; ++dy) {
    const int16_t dx = static_cast<int16_t>(isqrt(static_cast<int>(r) * r - static_cast<int>(dy) * dy));
    ssd1306HLine(cx - dx, cy + dy, static_cast<int16_t>(2 * dx + 1), on);
  }
}

void ssd1306Display() {
  if (!ready) {
    return;
  }
  // Page mode: cheap SSD1306 clones and SH1106 ignore horizontal 0x21/0x22
  // windows and stay blank even though the I2C address ACKs.
  for (uint8_t page = 0; page < (kOledHeight / 8); ++page) {
    sendCommand(static_cast<uint8_t>(0xB0 | page));
    sendCommand(0x00);
    sendCommand(0x10);
    const uint8_t* row = buffer + static_cast<size_t>(page) * kOledWidth;
    for (size_t i = 0; i < kOledWidth; i += 16) {
      Wire.beginTransmission(address);
      Wire.write(static_cast<uint8_t>(0x40));
      Wire.write(row + i, 16);
      Wire.endTransmission();
    }
  }
}
