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
}
