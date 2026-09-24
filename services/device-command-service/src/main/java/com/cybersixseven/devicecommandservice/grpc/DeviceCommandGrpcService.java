package com.cybersixseven.devicecommandservice.grpc;

import com.cybersixseven.contracts.device.DeviceCommandGrpc;
import com.cybersixseven.contracts.device.DeviceCommandRequest;
import com.cybersixseven.contracts.device.DeviceCommandResponse;
import com.cybersixseven.devicecommandservice.command.DeviceCommandMessage;
import com.cybersixseven.devicecommandservice.command.DeviceEventStates;
import com.cybersixseven.devicecommandservice.dynamodb.DeviceEventItem;
import com.cybersixseven.devicecommandservice.dynamodb.DeviceEventStore;
import com.cybersixseven.devicecommandservice.mqtt.MqttCommandPublisher;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class DeviceCommandGrpcService extends DeviceCommandGrpc.DeviceCommandImplBase {

  private static final Logger log = LoggerFactory.getLogger(DeviceCommandGrpcService.class);

  private final DeviceEventStore deviceEventStore;
  private final ObjectProvider<MqttCommandPublisher> mqttPublisher;

  public DeviceCommandGrpcService(
      DeviceEventStore deviceEventStore, ObjectProvider<MqttCommandPublisher> mqttPublisher) {
    this.deviceEventStore = deviceEventStore;
    this.mqttPublisher = mqttPublisher;
  }

  @Override
  public void sendCommand(
      DeviceCommandRequest request, StreamObserver<DeviceCommandResponse> responseObserver) {
    try {
      DeviceCommandMessage incoming = parse(request);
      DeviceEventItem existing = deviceEventStore.find(incoming.commandId().toString()).orElse(null);
      int intensity = existing != null && existing.getIntensity() != null
          ? existing.getIntensity()
          : incoming.intensity();
      DeviceCommandMessage toSend = incoming.withIntensity(DeviceCommandMessage.clampIntensity(intensity));
      MqttCommandPublisher publisher = mqttPublisher.getIfAvailable();
      if (publisher == null) {
        responseObserver.onNext(response(request.getCommandId(), false, "command service unavailable"));
        responseObserver.onCompleted();
        return;
      }
      if (existing == null) {
        deviceEventStore.createPendingIfAbsent(toSend);
      }
      publisher.publishCommand(toSend);
      if (existing == null || !DeviceEventStates.isComplete(existing.getState())) {
        boolean sent = deviceEventStore.markSent(toSend.commandId().toString());
        if (!sent) {
          throw new IllegalStateException("SENT transition failed");
        }
      }
      log.info(
          "grpc resend commandId={} deviceId={} intensity={}",
          toSend.commandId(),
          toSend.deviceId(),
          toSend.intensity());
      responseObserver.onNext(response(request.getCommandId(), true, "republished"));
      responseObserver.onCompleted();
    } catch (IllegalArgumentException ex) {
      responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("invalid command").asRuntimeException());
    } catch (RuntimeException ex) {
      log.error("grpc resend failed commandId={}", request.getCommandId(), ex);
      responseObserver.onError(
          Status.UNAVAILABLE.withDescription("command service unavailable").asRuntimeException());
    }
  }

  private static DeviceCommandMessage parse(DeviceCommandRequest request) {
    return new DeviceCommandMessage(
        UUID.fromString(request.getCommandId()),
        UUID.fromString(request.getSubmissionId()),
        request.getDeviceId(),
        request.getEvent(),
        request.getIntensity());
  }

  private static DeviceCommandResponse response(String commandId, boolean accepted, String message) {
    return DeviceCommandResponse.newBuilder()
        .setCommandId(commandId)
        .setAccepted(accepted)
        .setMessage(message)
        .build();
  }
}
