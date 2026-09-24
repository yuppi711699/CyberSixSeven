package com.cybersixseven.devicecommandservice.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cybersixseven.contracts.device.DeviceCommandGrpc;
import com.cybersixseven.contracts.device.DeviceCommandRequest;
import com.cybersixseven.contracts.device.DeviceCommandResponse;
import com.cybersixseven.devicecommandservice.command.DeviceCommandMessage;
import com.cybersixseven.devicecommandservice.command.DeviceEventStates;
import com.cybersixseven.devicecommandservice.dynamodb.DeviceEventItem;
import com.cybersixseven.devicecommandservice.dynamodb.DeviceEventStore;
import com.cybersixseven.devicecommandservice.mqtt.MqttCommandPublisher;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class DeviceCommandGrpcRoundTripTests {

  private static final String KEY = "test-internal-api-key";
  private static final String COMMAND_ID = "11111111-1111-1111-1111-111111111111";

  @MockitoBean
  private MqttCommandPublisher mqttCommandPublisher;

  @MockitoBean
  private DeviceEventStore deviceEventStore;

  @LocalGrpcServerPort
  private int port;

  @Test
  void missingKeyIsUnauthenticated() {
    StatusRuntimeException error = assertThrows(
        StatusRuntimeException.class, () -> call(null, request(COMMAND_ID, 3)));
    assertEquals(Status.Code.UNAUTHENTICATED, error.getStatus().getCode());
  }

  @Test
  void sendCommandRoundTripPreservesCommandId() {
    when(deviceEventStore.find(COMMAND_ID)).thenReturn(Optional.empty());
    when(deviceEventStore.markSent(COMMAND_ID)).thenReturn(true);

    DeviceCommandResponse response = call(KEY, request(COMMAND_ID, 3));

    assertEquals(COMMAND_ID, response.getCommandId());
    assertTrue(response.getAccepted());
    assertEquals("republished", response.getMessage());
    ArgumentCaptor<DeviceCommandMessage> captor = ArgumentCaptor.forClass(DeviceCommandMessage.class);
    verify(mqttCommandPublisher).publishCommand(captor.capture());
    assertEquals(UUID.fromString(COMMAND_ID), captor.getValue().commandId());
  }

  @Test
  void acknowledgedCommandIsRepublishedWithStoredIntensity() {
    DeviceEventItem item = new DeviceEventItem();
    item.setState(DeviceEventStates.ACKNOWLEDGED);
    item.setIntensity(5);
    item.setCommandId(COMMAND_ID);
    when(deviceEventStore.find(COMMAND_ID)).thenReturn(Optional.of(item));

    DeviceCommandResponse response = call(KEY, request(COMMAND_ID, 3));

    assertTrue(response.getAccepted());
    assertEquals(COMMAND_ID, response.getCommandId());
    ArgumentCaptor<DeviceCommandMessage> captor = ArgumentCaptor.forClass(DeviceCommandMessage.class);
    verify(mqttCommandPublisher).publishCommand(captor.capture());
    assertEquals(5, captor.getValue().intensity());
  }

  private DeviceCommandResponse call(String key, DeviceCommandRequest request) {
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
    try {
      DeviceCommandGrpc.DeviceCommandBlockingStub stub =
          DeviceCommandGrpc.newBlockingStub(channel).withDeadlineAfter(2, TimeUnit.SECONDS);
      if (key != null) {
        Metadata headers = new Metadata();
        headers.put(InternalApiKeyServerInterceptor.KEY, key);
        stub = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
      }
      return stub.sendCommand(request);
    } finally {
      channel.shutdownNow();
    }
  }

  private static DeviceCommandRequest request(String commandId, int intensity) {
    return DeviceCommandRequest.newBuilder()
        .setCommandId(commandId)
        .setSubmissionId("22222222-2222-2222-2222-222222222222")
        .setDeviceId("esp32-dev-001")
        .setEvent("correct")
        .setIntensity(intensity)
        .build();
  }
}
