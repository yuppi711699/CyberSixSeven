#include "lcd1602.h"

#include "lcd_type.h"

#include <Wire.h>

namespace {

constexpr uint32_t kI2cHz = 100000;
constexpr uint8_t kEn = 0x04;
constexpr uint8_t kRs = 0x01;
constexpr uint8_t kBl = 0x08;
constexpr uint8_t kCols = 16;

uint8_t address = 0;
bool ready = false;

bool probe(uint8_t addr) {
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

void expanderWrite(uint8_t data) {
  Wire.beginTransmission(address);
  Wire.write(static_cast<uint8_t>(data | kBl));
  Wire.endTransmission();
}

void pulseEnable(uint8_t data) {
  expanderWrite(static_cast<uint8_t>(data | kEn));
  delayMicroseconds(1);
  expanderWrite(data);
  delayMicroseconds(50);
}

void writeNibble(uint8_t nibbleHi, uint8_t rs) {
  const uint8_t data = static_cast<uint8_t>((nibbleHi & 0xF0) | rs);
  expanderWrite(data);
  pulseEnable(data);
}

void send(uint8_t value, uint8_t rs) {
  writeNibble(value, rs);
  writeNibble(static_cast<uint8_t>(value << 4), rs);
}

void command(uint8_t value) {
  send(value, 0);
}

void writeChar(uint8_t value) {
  send(value, kRs);
}

void writeLine(uint8_t row, const char* text) {
  command(row == 0 ? 0x80 : 0xC0);
  uint8_t i = 0;
  if (text != nullptr) {
    for (; i < kCols && text[i] != '\0'; ++i) {
      writeChar(static_cast<uint8_t>(text[i]));
    }
  }
  for (; i < kCols; ++i) {
    writeChar(static_cast<uint8_t>(' '));
  }
}

void scanBus() {
  Serial.print("[lcd] 1602 backpack not found — i2c scan:");
  bool any = false;
  for (uint8_t a = 0x08; a < 0x78; ++a) {
    if (probe(a)) {
      Serial.printf(" 0x%02X", a);
      any = true;
    }
  }
  if (!any) {
    Serial.print(" (none)");
  }
  Serial.println(" — VCC=5V GND SDA=21 SCL=22, contrast pot on backpack");
}

}  // namespace

bool lcd1602Begin() {
  ready = false;
  address = 0;
  Wire.begin(kLcdSdaPin, kLcdSclPin);
  Wire.setClock(kI2cHz);
  delay(50);

  // 0x27 / 0x3F cover almost every YwRobot-style backpack. Then the rest of
  // the PCF8574 (0x20-0x27) and PCF8574A (0x38-0x3F) straps.
  const uint8_t preferred[] = {0x27, 0x3F};
  for (uint8_t i = 0; i < sizeof(preferred); ++i) {
    if (probe(preferred[i])) {
      address = preferred[i];
      break;
    }
  }
  if (address == 0) {
    for (uint8_t a = 0x20; a <= 0x27; ++a) {
      if (probe(a)) {
        address = a;
        break;
      }
    }
  }
  if (address == 0) {
    for (uint8_t a = 0x38; a <= 0x3F; ++a) {
      if (probe(a)) {
        address = a;
        break;
      }
    }
  }
  if (address == 0) {
    scanBus();
    return false;
  }

  // HD44780 4-bit init. delay() is legal in setup; loop() never calls this.
  delay(50);
  writeNibble(0x30, 0);
  delay(5);
  writeNibble(0x30, 0);
  delay(5);
  writeNibble(0x30, 0);
  delay(1);
  writeNibble(0x20, 0);

  command(0x28);
  command(0x08);
  command(0x01);
  delay(2);
  command(0x06);
  command(0x0C);

  ready = true;
  lcd1602SetLines("CyberSixSeven", "lcd1602 ok");
  delay(300);
  return true;
}

bool lcd1602Ready() {
  return ready;
}

uint8_t lcd1602Address() {
  return address;
}

void lcd1602SetLines(const char* line0, const char* line1) {
  if (!ready) {
    return;
  }
  writeLine(0, line0);
  writeLine(1, line1);
}
