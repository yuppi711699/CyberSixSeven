package com.cybersixseven.devicecommandservice.command;

import java.util.UUID;

public record DeviceCommandMessage(
    UUID commandId,
    UUID submissionId,
    String deviceId,
    String event,
    int intensity,
    Integer score,
    Integer totalQuestions) {

  public static final int DEFAULT_INTENSITY = 3;
  public static final int MIN_INTENSITY = 1;
  public static final int MAX_INTENSITY = 5;

  public DeviceCommandMessage(
      UUID commandId, UUID submissionId, String deviceId, String event, int intensity) {
    this(commandId, submissionId, deviceId, event, intensity, null, null);
  }

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

  public DeviceCommandMessage withIntensity(int nextIntensity) {
    return new DeviceCommandMessage(
        commandId, submissionId, deviceId, event, nextIntensity, score, totalQuestions);
  }

  public static int clampIntensity(int value) {
    if (value < MIN_INTENSITY) {
      return MIN_INTENSITY;
    }
    if (value > MAX_INTENSITY) {
      return MAX_INTENSITY;
    }
    return value;
  }
}
