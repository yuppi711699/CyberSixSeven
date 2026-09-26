#pragma once

#include <cstdint>
#include <cstring>

// Host-testable command reliability. No Arduino, Wi-Fi, or MQTT types.

namespace c67 {

constexpr uint8_t kCommandRingSize = 64;
constexpr uint32_t kMqttBackoffMinMs = 1000;
constexpr uint32_t kMqttBackoffMaxMs = 30000;

enum class NetPhase : uint8_t { Wifi, Ntp, Mqtt, Ready };

enum class PayloadReject : uint8_t { Ok, MissingField, UnknownEvent, Intensity };

struct CommandRing {
  uint8_t next;
  uint8_t count;
  char ids[kCommandRingSize][37];
};

inline bool ringValid(const CommandRing& ring) {
  return ring.count <= kCommandRingSize && ring.next < kCommandRingSize;
}

inline bool ringContains(const CommandRing& ring, const char* commandId) {
  if (commandId == nullptr) {
    return false;
  }
  const uint8_t n = ring.count;
  for (uint8_t i = 0; i < n; ++i) {
    if (strncmp(ring.ids[i], commandId, 36) == 0) {
      return true;
    }
  }
  return false;
}

inline void ringRemember(CommandRing& ring, const char* commandId) {
  strncpy(ring.ids[ring.next], commandId, 36);
  ring.ids[ring.next][36] = '\0';
  ring.next = static_cast<uint8_t>((ring.next + 1) % kCommandRingSize);
  if (ring.count < kCommandRingSize) {
    ring.count++;
  }
}

inline uint32_t nextMqttBackoff(uint32_t currentMs) {
  if (currentMs >= kMqttBackoffMaxMs) {
    return kMqttBackoffMaxMs;
  }
  const uint64_t doubled = static_cast<uint64_t>(currentMs) * 2u;
  if (doubled > kMqttBackoffMaxMs) {
    return kMqttBackoffMaxMs;
  }
  return static_cast<uint32_t>(doubled);
}

inline NetPhase phaseAfterWifi(bool wifiUp, NetPhase phase) {
  if (!wifiUp) {
    return NetPhase::Wifi;
  }
  if (phase == NetPhase::Wifi) {
    return NetPhase::Ntp;
  }
  return phase;
}

inline NetPhase phaseAfterClock(bool clockReady, NetPhase phase) {
  if (phase == NetPhase::Ntp && clockReady) {
    return NetPhase::Mqtt;
  }
  return phase;
}

inline PayloadReject acceptCommand(
    const char* commandId,
    const char* submissionId,
    const char* event,
    bool intensityIsInt,
    int intensity) {
  if (commandId == nullptr || commandId[0] == '\0' || submissionId == nullptr || submissionId[0] == '\0'
      || event == nullptr) {
    return PayloadReject::MissingField;
  }
  if (strcmp(event, "correct") != 0 && strcmp(event, "incorrect") != 0) {
    return PayloadReject::UnknownEvent;
  }
  if (!intensityIsInt || intensity < 1 || intensity > 5) {
    return PayloadReject::Intensity;
  }
  return PayloadReject::Ok;
}

}  // namespace c67
