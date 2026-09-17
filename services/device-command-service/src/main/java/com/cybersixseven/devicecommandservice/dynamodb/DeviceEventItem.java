package com.cybersixseven.devicecommandservice.dynamodb;

import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
public class DeviceEventItem {

  private String commandId;
  private String submissionId;
  private String deviceId;
  private String event;
  private Integer intensity;
  private Integer attempts;
  private String state;
  private String createdAt;
  private String sentAt;
  private String acknowledgedAt;
  private String updatedAt;

  @DynamoDbPartitionKey
  public String getCommandId() {
    return commandId;
  }

  public void setCommandId(String commandId) {
    this.commandId = commandId;
  }

  public String getSubmissionId() {
    return submissionId;
  }

  public void setSubmissionId(String submissionId) {
    this.submissionId = submissionId;
  }

  public String getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(String deviceId) {
    this.deviceId = deviceId;
  }

  public String getEvent() {
    return event;
  }

  public void setEvent(String event) {
    this.event = event;
  }

  public Integer getIntensity() {
    return intensity;
  }

  public void setIntensity(Integer intensity) {
    this.intensity = intensity;
  }

  public Integer getAttempts() {
    return attempts;
  }

  public void setAttempts(Integer attempts) {
    this.attempts = attempts;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public String getSentAt() {
    return sentAt;
  }

  public void setSentAt(String sentAt) {
    this.sentAt = sentAt;
  }

  public String getAcknowledgedAt() {
    return acknowledgedAt;
  }

  public void setAcknowledgedAt(String acknowledgedAt) {
    this.acknowledgedAt = acknowledgedAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }

  public static String iso(Instant instant) {
    return instant.toString();
  }
}
