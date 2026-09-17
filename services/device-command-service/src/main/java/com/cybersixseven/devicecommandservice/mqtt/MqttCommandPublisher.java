package com.cybersixseven.devicecommandservice.mqtt;

import com.cybersixseven.devicecommandservice.command.DeviceCommandMessage;

public interface MqttCommandPublisher {

  void publishCommand(DeviceCommandMessage command);
}
