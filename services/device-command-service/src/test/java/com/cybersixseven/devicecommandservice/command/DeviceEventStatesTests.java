package com.cybersixseven.devicecommandservice.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeviceEventStatesTests {

  @Test
  void sentAndAcknowledgedAreComplete() {
    assertTrue(DeviceEventStates.isComplete(DeviceEventStates.SENT));
    assertTrue(DeviceEventStates.isComplete(DeviceEventStates.ACKNOWLEDGED));
    assertFalse(DeviceEventStates.isComplete(DeviceEventStates.PENDING));
  }

  @Test
  void pendingAndSentCanBeAcknowledged() {
    assertTrue(DeviceEventStates.canAcknowledge(DeviceEventStates.PENDING));
    assertTrue(DeviceEventStates.canAcknowledge(DeviceEventStates.SENT));
    assertFalse(DeviceEventStates.canAcknowledge(DeviceEventStates.ACKNOWLEDGED));
    assertFalse(DeviceEventStates.canAcknowledge("UNKNOWN"));
  }
}
