#include "lcd1602.h"

#include "lcd_type.h"

#include <Wire.h>

namespace {

constexpr uint32_t kI2cHz = 100000;
constexpr uint8_t kCols = 16;
constexpr uint8_t kGravityLcdAddr = 0x3E;
constexpr uint8_t kGravityControlCmd = 0x80;
constexpr uint8_t kGravityControlData = 0x40;

enum class Kind : uint8_t { None, Pcf8574, Pcf8574A, Gravity };

struct PcfMap {
  uint8_t rs;
  uint8_t rw;
  uint8_t en;
  uint8_t bl;
  uint8_t d4;
  uint8_t d5;
  uint8_t d6;
  uint8_t d7;
};

// YwRobot / LCM1602: P0=RS P1=RW P2=EN P3=BL P4-P7=D4-D7
constexpr PcfMap kMapYwRobot = {0, 1, 2, 3, 4, 5, 6, 7};
// MJKDZ: P0-P3=D4-D7 P4=EN P5=RW P6=RS P7=BL
constexpr PcfMap kMapMjkdz = {6, 5, 4, 7, 0, 1, 2, 3};

Kind kind = Kind::None;
const PcfMap* pcfMap = &kMapYwRobot;
uint8_t lcdAddr = 0;
uint8_t rgbAddr = 0;
uint8_t rgbRedReg = 0;
uint8_t rgbGreenReg = 0;
uint8_t rgbBlueReg = 0;
bool ready = false;

uint8_t pinBit(uint8_t pin) {
  return static_cast<uint8_t>(1u << pin);
}

bool probe(uint8_t addr) {
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

void expanderWrite(uint8_t data) {
  Wire.beginTransmission(lcdAddr);
  Wire.write(data);
  Wire.endTransmission();
}

void pulseEnable(uint8_t data) {
  expanderWrite(static_cast<uint8_t>(data | pinBit(pcfMap->en)));
  delayMicroseconds(1);
  expanderWrite(data);
  delayMicroseconds(50);
}

uint8_t packNibble(uint8_t nibbleHi, bool rs, bool rw) {
  uint8_t data = pinBit(pcfMap->bl);
  if (rs) {
    data = static_cast<uint8_t>(data | pinBit(pcfMap->rs));
  }
  if (rw) {
    data = static_cast<uint8_t>(data | pinBit(pcfMap->rw));
  }
  if ((nibbleHi & 0x10) != 0) {
    data = static_cast<uint8_t>(data | pinBit(pcfMap->d4));
  }
  if ((nibbleHi & 0x20) != 0) {
    data = static_cast<uint8_t>(data | pinBit(pcfMap->d5));
  }
  if ((nibbleHi & 0x40) != 0) {
    data = static_cast<uint8_t>(data | pinBit(pcfMap->d6));
  }
  if ((nibbleHi & 0x80) != 0) {
    data = static_cast<uint8_t>(data | pinBit(pcfMap->d7));
  }
  return data;
}

void writeNibble(uint8_t nibbleHi, bool rs) {
  const uint8_t data = packNibble(nibbleHi, rs, false);
  expanderWrite(data);
  pulseEnable(data);
}

void pcfSend(uint8_t value, bool rs) {
  writeNibble(value, rs);
  writeNibble(static_cast<uint8_t>(value << 4), rs);
}

uint8_t readNibble() {
  const uint8_t data = packNibble(0xF0, false, true);
  expanderWrite(static_cast<uint8_t>(data | pinBit(pcfMap->en)));
  delayMicroseconds(1);
  Wire.requestFrom(lcdAddr, static_cast<uint8_t>(1));
  uint8_t val = 0xFF;
  if (Wire.available() > 0) {
    val = static_cast<uint8_t>(Wire.read());
  }
  expanderWrite(data);
  delayMicroseconds(50);
  uint8_t nibble = 0;
  if ((val & pinBit(pcfMap->d4)) != 0) {
    nibble = static_cast<uint8_t>(nibble | 0x10);
  }
  if ((val & pinBit(pcfMap->d5)) != 0) {
    nibble = static_cast<uint8_t>(nibble | 0x20);
  }
  if ((val & pinBit(pcfMap->d6)) != 0) {
    nibble = static_cast<uint8_t>(nibble | 0x40);
  }
  if ((val & pinBit(pcfMap->d7)) != 0) {
    nibble = static_cast<uint8_t>(nibble | 0x80);
  }
  return nibble;
}

bool pcfBusyCleared() {
  const uint8_t hi = readNibble();
  (void)readNibble();
  return (hi & 0x80) == 0;
}

void gravitySend(uint8_t control, uint8_t value) {
  Wire.beginTransmission(lcdAddr);
  Wire.write(control);
  delayMicroseconds(100);
  Wire.write(value);
  delayMicroseconds(100);
  Wire.endTransmission();
}

void command(uint8_t value) {
  if (kind == Kind::Gravity) {
    gravitySend(kGravityControlCmd, value);
  } else {
    pcfSend(value, false);
  }
}

void writeChar(uint8_t value) {
  if (kind == Kind::Gravity) {
    gravitySend(kGravityControlData, value);
  } else {
    pcfSend(value, true);
  }
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

void rgbSetReg(uint8_t reg, uint8_t data) {
  Wire.beginTransmission(rgbAddr);
  Wire.write(reg);
  Wire.write(data);
  Wire.endTransmission();
}

void configureRgb(uint8_t addr) {
  rgbAddr = addr;
  if (addr == 0x60) {
    rgbRedReg = 0x04;
    rgbGreenReg = 0x03;
    rgbBlueReg = 0x02;
    rgbSetReg(0x00, 0x00);
    rgbSetReg(0x08, 0xFF);
    rgbSetReg(0x01, 0x20);
    return;
  }
  if (addr == 0x6B) {
    rgbRedReg = 0x06;
    rgbGreenReg = 0x05;
    rgbBlueReg = 0x04;
    rgbSetReg(0x2F, 0x00);
    rgbSetReg(0x00, 0x20);
    rgbSetReg(0x01, 0x00);
    rgbSetReg(0x02, 0x01);
    rgbSetReg(0x03, 4);
    return;
  }
  rgbRedReg = 0x01;
  rgbGreenReg = 0x02;
  rgbBlueReg = 0x03;
}

bool gravityRgbPresent() {
  const uint8_t candidates[] = {0x60, 0x6B, 0x2D};
  for (uint8_t i = 0; i < sizeof(candidates); ++i) {
    if (probe(candidates[i])) {
      configureRgb(candidates[i]);
      return true;
    }
  }
  return false;
}

void pcfInit4Bit() {
  delay(100);
  writeNibble(0x30, false);
  delay(5);
  writeNibble(0x30, false);
  delay(5);
  writeNibble(0x30, false);
  delay(1);
  writeNibble(0x20, false);
  command(0x28);
  command(0x08);
  command(0x01);
  delay(2);
  command(0x06);
  command(0x0C);
}

void gravityInit() {
  delay(50);
  command(0x28);
  delay(5);
  command(0x28);
  delay(5);
  command(0x28);
  command(0x0C);
  command(0x01);
  delay(2);
  command(0x06);
}

bool tryPcfMap(const PcfMap* map) {
  pcfMap = map;
  pcfInit4Bit();
  delay(2);
  return pcfBusyCleared();
}

void strobeBacklight() {
  // P3 = YwRobot BL, P7 = MJKDZ BL. 0x00 vs 0xFF covers both polarities.
  Serial.println("[lcd] strobing backlight — you should see the LED blink");
  for (uint8_t i = 0; i < 6; ++i) {
    expanderWrite(0x00);
    delay(120);
    expanderWrite(0xFF);
    delay(120);
  }
  expanderWrite(static_cast<uint8_t>(pinBit(3) | pinBit(7)));
}

void defineBlockGlyph() {
  command(0x40);
  for (uint8_t i = 0; i < 8; ++i) {
    writeChar(0x1F);
  }
}

void fillBlockRows() {
  command(0x80);
  for (uint8_t i = 0; i < kCols; ++i) {
    writeChar(0);
  }
  command(0xC0);
  for (uint8_t i = 0; i < kCols; ++i) {
    writeChar(0);
  }
}

void proveGlass() {
  defineBlockGlyph();
  command(0x0F);
  fillBlockRows();
  Serial.println("[lcd] 32 black blocks + blinking cursor. twist contrast pot if glow but no bars");
  delay(1200);
  command(0x0C);
}

void scanBus() {
  Serial.print("[lcd] 1602 not found — i2c scan:");
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
  Serial.println(" — PCF8574A is 0x38-0x3F (usually 0x3F); PCF8574 is 0x20-0x27; Gravity needs 0x3E+RGB");
}

Kind kindFromAddr(uint8_t addr) {
  if (addr >= 0x38 && addr <= 0x3F) {
    return Kind::Pcf8574A;
  }
  return Kind::Pcf8574;
}

bool claimPcf(uint8_t addr) {
  kind = kindFromAddr(addr);
  lcdAddr = addr;
  return true;
}

}  // namespace

bool lcd1602Begin() {
  ready = false;
  kind = Kind::None;
  lcdAddr = 0;
  rgbAddr = 0;
  pcfMap = &kMapYwRobot;
  Wire.begin(kLcdSdaPin, kLcdSclPin);
  Wire.setClock(kI2cHz);
  delay(50);

  // Gravity DFR0464 is 0x3E plus a second RGB address. 0x3E alone is PCF8574A.
  if (probe(kGravityLcdAddr) && gravityRgbPresent()) {
    kind = Kind::Gravity;
    lcdAddr = kGravityLcdAddr;
  }

  if (kind == Kind::None) {
    const uint8_t pcf8574a[] = {0x3F, 0x3E, 0x3C, 0x3D, 0x3B, 0x3A, 0x39, 0x38};
    for (uint8_t i = 0; i < sizeof(pcf8574a); ++i) {
      if (probe(pcf8574a[i])) {
        claimPcf(pcf8574a[i]);
        break;
      }
    }
  }
  if (kind == Kind::None) {
    const uint8_t pcf8574[] = {0x27, 0x26, 0x25, 0x24, 0x23, 0x22, 0x21, 0x20};
    for (uint8_t i = 0; i < sizeof(pcf8574); ++i) {
      if (probe(pcf8574[i])) {
        claimPcf(pcf8574[i]);
        break;
      }
    }
  }

  if (kind == Kind::None) {
    scanBus();
    return false;
  }

  if (kind == Kind::Gravity) {
    gravityInit();
    ready = true;
    Serial.println("[lcd] RGB flash then blocks");
    lcd1602SetRgb(255, 0, 0);
    delay(200);
    lcd1602SetRgb(0, 255, 0);
    delay(200);
    lcd1602SetRgb(0, 0, 255);
    delay(200);
    lcd1602SetRgb(255, 255, 255);
    proveGlass();
    lcd1602SetLines("CyberSixSeven", "gravity ok");
    delay(300);
    return true;
  }

  strobeBacklight();
  if (!tryPcfMap(&kMapYwRobot)) {
    Serial.println("[lcd] ywrobot map busy stuck — trying mjkdz pinout");
    tryPcfMap(&kMapMjkdz);
  }

  ready = true;
  proveGlass();
  lcd1602SetLines("CyberSixSeven", kind == Kind::Pcf8574A ? "pcf8574a ok" : "pcf8574 ok");
  delay(300);
  return true;
}

bool lcd1602Ready() {
  return ready;
}

uint8_t lcd1602Address() {
  return lcdAddr;
}

uint8_t lcd1602RgbAddress() {
  return rgbAddr;
}

const char* lcd1602Kind() {
  if (kind == Kind::Gravity) {
    return "gravity";
  }
  if (kind == Kind::Pcf8574A) {
    return "pcf8574a";
  }
  if (kind == Kind::Pcf8574) {
    return "pcf8574";
  }
  return "none";
}

void lcd1602SetLines(const char* line0, const char* line1) {
  if (!ready) {
    return;
  }
  writeLine(0, line0);
  writeLine(1, line1);
}

void lcd1602SetRgb(uint8_t r, uint8_t g, uint8_t b) {
  if (!ready || rgbAddr == 0) {
    return;
  }
  rgbSetReg(rgbRedReg, r);
  rgbSetReg(rgbGreenReg, g);
  rgbSetReg(rgbBlueReg, b);
  if (rgbAddr == 0x6B) {
    rgbSetReg(0x07, 0xFF);
  }
}
