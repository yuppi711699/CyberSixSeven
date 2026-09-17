package com.cybersixseven.devicecommandservice.heartbeat;

public interface PlatformHeartbeatClient {

  void report(String deviceId, String submissionId, String commandId, String status);
}
