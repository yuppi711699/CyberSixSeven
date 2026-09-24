package com.cybersixseven.devicecommandservice.mqtt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cybersixseven.devicecommandservice.dynamodb.DeviceEventStore;
import com.cybersixseven.devicecommandservice.heartbeat.PlatformHeartbeatClient;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PahoMqttCommandBridgeTests {

  @Mock
  private DeviceEventStore deviceEventStore;

  @Mock
  private PlatformHeartbeatClient heartbeatClient;

  private PahoMqttCommandBridge bridge;
  private UUID commandId;
  private UUID submissionId;

  @BeforeEach
  void setUp() {
    commandId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    submissionId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    bridge = new PahoMqttCommandBridge(
        deviceEventStore,
        heartbeatClient,
        new JsonMapper(),
        "ssl://127.0.0.1:1",
        "test-client",
        "/tmp/ca.crt",
        "/tmp/client.crt",
        "/tmp/client.key",
        1,
        1);
  }

  @Test
  void acknowledgementRecordsCommandIdAndReportsHeartbeat() {
    when(deviceEventStore.markAcknowledged(commandId.toString())).thenReturn(true);

    bridge.messageArrived("devices/esp32-dev-001/status", json("""
        {"deviceId":"esp32-dev-001","submissionId":"%s","commandId":"%s","status":"handled"}
        """.formatted(submissionId, commandId)));

    verify(deviceEventStore).markAcknowledged(commandId.toString());
    verify(heartbeatClient).report("esp32-dev-001", submissionId.toString(), commandId.toString(), "handled");
  }

  @Test
  void missingRequiredFieldsAreIgnored() {
    bridge.messageArrived("devices/esp32-dev-001/status", json("""
        {"deviceId":"esp32-dev-001","commandId":"%s","status":"handled"}
        """.formatted(commandId)));

    verify(deviceEventStore, never()).markAcknowledged(any());
    verify(heartbeatClient, never()).report(any(), any(), any(), any());
  }

  @Test
  void invalidCommandIdDoesNotAckOrHeartbeat() {
    bridge.messageArrived("devices/esp32-dev-001/status", json("""
        {"deviceId":"esp32-dev-001","submissionId":"%s","commandId":"not-a-uuid","status":"handled"}
        """.formatted(submissionId)));

    verify(deviceEventStore, never()).markAcknowledged(any());
    verify(heartbeatClient, never()).report(any(), any(), any(), any());
  }

  @Test
  void publishWithoutConnectionFails() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () -> bridge.publishCommand(new com.cybersixseven.devicecommandservice.command.DeviceCommandMessage(
            commandId, submissionId, "esp32-dev-001", "correct", 3)));
  }

  private static MqttMessage json(String body) {
    return new MqttMessage(body.getBytes(StandardCharsets.UTF_8));
  }
}
