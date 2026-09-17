package com.cybersixseven.devicecommandservice.command;

public final class DeviceEventStates {

  public static final String PENDING = "PENDING";
  public static final String SENT = "SENT";
  public static final String ACKNOWLEDGED = "ACKNOWLEDGED";

  private DeviceEventStates() {}

  public static boolean isComplete(String state) {
    return SENT.equals(state) || ACKNOWLEDGED.equals(state);
  }

  public static boolean canAcknowledge(String state) {
    return PENDING.equals(state) || SENT.equals(state);
  }
}
