#include "reliability.h"

#include <cstdio>
#include <cstdlib>

#define EXPECT(cond)                                                                 \
  do {                                                                               \
    if (!(cond)) {                                                                   \
      std::fprintf(stderr, "FAIL %s:%d %s\n", __FILE__, __LINE__, #cond);           \
      std::exit(1);                                                                  \
    }                                                                                \
  } while (0)

int main() {
  EXPECT(c67::nextMqttBackoff(1000) == 2000);
  EXPECT(c67::nextMqttBackoff(16000) == 30000);
  EXPECT(c67::nextMqttBackoff(30000) == 30000);

  EXPECT(c67::phaseAfterWifi(false, c67::NetPhase::Ready) == c67::NetPhase::Wifi);
  EXPECT(c67::phaseAfterWifi(true, c67::NetPhase::Wifi) == c67::NetPhase::Ntp);
  EXPECT(c67::phaseAfterWifi(true, c67::NetPhase::Mqtt) == c67::NetPhase::Mqtt);
  EXPECT(c67::phaseAfterClock(false, c67::NetPhase::Ntp) == c67::NetPhase::Ntp);
  EXPECT(c67::phaseAfterClock(true, c67::NetPhase::Ntp) == c67::NetPhase::Mqtt);

  c67::CommandRing ring{};
  EXPECT(c67::ringValid(ring));
  ring.count = 65;
  EXPECT(!c67::ringValid(ring));
  ring = c67::CommandRing{};

  const char* first = "11111111-1111-1111-1111-111111111111";
  c67::ringRemember(ring, first);
  EXPECT(c67::ringContains(ring, first));
  EXPECT(!c67::ringContains(ring, "22222222-2222-2222-2222-222222222222"));

  for (int i = 0; i < c67::kCommandRingSize; ++i) {
    char id[37];
    std::snprintf(id, sizeof(id), "33333333-3333-3333-3333-%012d", i);
    c67::ringRemember(ring, id);
  }
  EXPECT(ring.count == c67::kCommandRingSize);
  EXPECT(!c67::ringContains(ring, first));
  EXPECT(c67::ringValid(ring));

  EXPECT(c67::acceptCommand(nullptr, "sub", "correct", true, 3) == c67::PayloadReject::MissingField);
  EXPECT(c67::acceptCommand("cmd", "sub", "nope", true, 3) == c67::PayloadReject::UnknownEvent);
  EXPECT(c67::acceptCommand("cmd", "sub", "correct", true, 9) == c67::PayloadReject::Intensity);
  EXPECT(c67::acceptCommand("cmd", "sub", "incorrect", false, 3) == c67::PayloadReject::Intensity);
  EXPECT(c67::acceptCommand("cmd", "sub", "correct", true, 1) == c67::PayloadReject::Ok);
  EXPECT(c67::acceptCommand("", "sub", "correct", true, 3) == c67::PayloadReject::MissingField);
  EXPECT(c67::acceptCommand("cmd", "", "correct", true, 3) == c67::PayloadReject::MissingField);
  EXPECT(c67::acceptCommand("cmd", "sub", nullptr, true, 3) == c67::PayloadReject::MissingField);
  EXPECT(!c67::ringContains(ring, nullptr));
  EXPECT(c67::phaseAfterClock(false, c67::NetPhase::Ready) == c67::NetPhase::Ready);
  EXPECT(c67::phaseAfterWifi(true, c67::NetPhase::Ready) == c67::NetPhase::Ready);
  ring.next = 64;
  EXPECT(!c67::ringValid(ring));

  std::puts("reliability ok");
  return 0;
}
