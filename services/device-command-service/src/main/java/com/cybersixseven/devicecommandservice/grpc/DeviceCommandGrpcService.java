package com.cybersixseven.devicecommandservice.grpc;

import com.cybersixseven.contracts.device.DeviceCommandGrpc;
import com.cybersixseven.contracts.device.DeviceCommandRequest;
import com.cybersixseven.contracts.device.DeviceCommandResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class DeviceCommandGrpcService extends DeviceCommandGrpc.DeviceCommandImplBase {

  @Override
  public void sendCommand(
      DeviceCommandRequest request, StreamObserver<DeviceCommandResponse> responseObserver) {
    responseObserver.onNext(DeviceCommandResponse.newBuilder()
        .setCommandId(request.getCommandId())
        .setAccepted(false)
        .setMessage("SendCommand is reserved for admin resend in v0.6")
        .build());
    responseObserver.onCompleted();
  }
}
