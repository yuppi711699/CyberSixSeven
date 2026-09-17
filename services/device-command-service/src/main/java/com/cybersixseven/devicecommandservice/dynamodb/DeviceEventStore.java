package com.cybersixseven.devicecommandservice.dynamodb;

import com.cybersixseven.devicecommandservice.command.DeviceCommandMessage;
import java.util.Optional;

public interface DeviceEventStore {

  Optional<DeviceEventItem> find(String commandId);

  DeviceEventItem createPendingIfAbsent(DeviceCommandMessage command);

  boolean markSent(String commandId);

  boolean markAcknowledged(String commandId);
}
