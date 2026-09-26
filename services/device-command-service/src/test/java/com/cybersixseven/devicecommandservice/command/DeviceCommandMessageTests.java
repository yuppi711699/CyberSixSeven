package com.cybersixseven.devicecommandservice.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceCommandMessageTests {

  @Test
  void topicAndIdentity() {
    UUID commandId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    DeviceCommandMessage message =
        new DeviceCommandMessage(commandId, submissionId, "esp32-dev-001", "incorrect", 3);
    assertEquals("devices/esp32-dev-001/commands", message.commandTopic());
    assertEquals(commandId, message.commandId());
  }

  @Test
  void rejectsUnknownEvent() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new DeviceCommandMessage(
            UUID.randomUUID(), UUID.randomUUID(), "esp32-dev-001", "maybe", 3));
  }

  @Test
  void rejectsMissingIdentity() {
    UUID commandId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    assertThrows(
        IllegalArgumentException.class,
        () -> new DeviceCommandMessage(null, submissionId, "esp32-dev-001", "correct", 3));
    assertThrows(
        IllegalArgumentException.class,
        () -> new DeviceCommandMessage(commandId, null, "esp32-dev-001", "correct", 3));
    assertThrows(
        IllegalArgumentException.class,
        () -> new DeviceCommandMessage(commandId, submissionId, " ", "correct", 3));
  }

  @Test
  void clampsIntensityAndCopiesTheRest() {
    UUID commandId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    DeviceCommandMessage message =
        new DeviceCommandMessage(commandId, submissionId, "esp32-dev-001", "correct", 3, 2, 3);
    assertEquals(1, DeviceCommandMessage.clampIntensity(0));
    assertEquals(5, DeviceCommandMessage.clampIntensity(9));
    assertEquals(3, DeviceCommandMessage.clampIntensity(3));
    DeviceCommandMessage next = message.withIntensity(4);
    assertEquals(4, next.intensity());
    assertEquals(commandId, next.commandId());
    assertEquals(submissionId, next.submissionId());
    assertEquals(2, next.score());
    assertEquals(3, next.totalQuestions());
  }
}
