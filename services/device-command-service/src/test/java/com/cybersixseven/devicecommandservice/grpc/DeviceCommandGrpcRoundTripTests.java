package com.cybersixseven.devicecommandservice.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cybersixseven.contracts.device.DeviceCommandGrpc;
import com.cybersixseven.contracts.device.DeviceCommandRequest;
import com.cybersixseven.contracts.device.DeviceCommandResponse;
import com.cybersixseven.devicecommandservice.mqtt.MqttCommandPublisher;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class DeviceCommandGrpcRoundTripTests {

  @MockitoBean
  private MqttCommandPublisher mqttCommandPublisher;

  @LocalGrpcServerPort
  private int port;

  @Test
  void sendCommandRoundTripPreservesCommandId() throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port)
        .usePlaintext()
        .build();
    try {
      DeviceCommandGrpc.DeviceCommandBlockingStub stub =
          DeviceCommandGrpc.newBlockingStub(channel).withDeadlineAfter(2, TimeUnit.SECONDS);
      DeviceCommandResponse response = stub.sendCommand(DeviceCommandRequest.newBuilder()
          .setCommandId("cmd-1")
          .setSubmissionId("sub-1")
          .setDeviceId("esp32-dev-001")
          .setEvent("correct")
          .setIntensity(3)
          .build());
      assertEquals("cmd-1", response.getCommandId());
      assertFalse(response.getAccepted());
      assertEquals("SendCommand is reserved for admin resend in v0.6", response.getMessage());
    } finally {
      channel.shutdownNow();
    }
  }
}
