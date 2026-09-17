package com.cybersixseven.devicecommandservice.command;

import java.util.UUID;

public record DeviceCommandMessage(
    UUID commandId, UUID submissionId, String deviceId, String event, int intensity) {

  public static final int DEFAULT_INTENSITY = 3;

  public DeviceCommandMessage {
    if (commandId == null) {
      throw new IllegalArgumentException("commandId is required");
    }
    if (submissionId == null) {
      throw new IllegalArgumentException("submissionId is required");
    }
    if (deviceId == null || deviceId.isBlank()) {
      throw new IllegalArgumentException("deviceId is required");
    }
    if (!"correct".equals(event) && !"incorrect".equals(event)) {
      throw new IllegalArgumentException("event must be correct or incorrect");
    }
  }

  public String commandTopic() {
    return "devices/" + deviceId + "/commands";
  }
}
