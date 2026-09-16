package com.cybersixseven.contracts.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.junit.jupiter.api.Test;

class DeviceCommandProtoDescriptorTest {

  @Test
  void requestDescriptorExposesCommandSubmissionDeviceEventAndIntensity() {
    Descriptor descriptor = DeviceCommandRequest.getDescriptor();

    assertField(descriptor, "command_id", FieldDescriptor.Type.STRING);
    assertField(descriptor, "submission_id", FieldDescriptor.Type.STRING);
    assertField(descriptor, "device_id", FieldDescriptor.Type.STRING);
    assertField(descriptor, "event", FieldDescriptor.Type.STRING);
    assertField(descriptor, "intensity", FieldDescriptor.Type.INT32);
  }

  @Test
  void responseDescriptorExposesCommandIdAcceptedAndMessage() {
    Descriptor descriptor = DeviceCommandResponse.getDescriptor();

    assertField(descriptor, "command_id", FieldDescriptor.Type.STRING);
    assertField(descriptor, "accepted", FieldDescriptor.Type.BOOL);
    assertField(descriptor, "message", FieldDescriptor.Type.STRING);
  }

  @Test
  void serviceExposesSendCommandRpc() {
    io.grpc.ServiceDescriptor service = DeviceCommandGrpc.getServiceDescriptor();
    io.grpc.MethodDescriptor<DeviceCommandRequest, DeviceCommandResponse> method =
        DeviceCommandGrpc.getSendCommandMethod();

    assertNotNull(service);
    assertEquals("cybersixseven.device.DeviceCommand", service.getName());
    assertNotNull(method);
    assertEquals(
        "cybersixseven.device.DeviceCommand/SendCommand", method.getFullMethodName());
    assertEquals(io.grpc.MethodDescriptor.MethodType.UNARY, method.getType());
  }

  @Test
  void generatedStubAndMessageTypesCompile() {
    assertNotNull(DeviceCommandGrpc.class);
    assertNotNull(DeviceCommandGrpc.getServiceDescriptor());
    DeviceCommandRequest request =
        DeviceCommandRequest.newBuilder()
            .setCommandId("c")
            .setSubmissionId("s")
            .setDeviceId("esp32-dev-001")
            .setEvent("correct")
            .setIntensity(3)
            .build();
    DeviceCommandResponse response =
        DeviceCommandResponse.newBuilder()
            .setCommandId("c")
            .setAccepted(true)
            .setMessage("ok")
            .build();
    assertEquals("c", request.getCommandId());
    assertEquals(3, request.getIntensity());
    assertEquals(true, response.getAccepted());
  }

  private static void assertField(Descriptor descriptor, String name, FieldDescriptor.Type type) {
    FieldDescriptor field = descriptor.findFieldByName(name);
    assertNotNull(field, "missing field: " + name);
    assertEquals(type, field.getType(), name);
  }
}
