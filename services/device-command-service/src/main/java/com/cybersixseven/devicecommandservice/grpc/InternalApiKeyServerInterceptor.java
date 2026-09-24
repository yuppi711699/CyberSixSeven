package com.cybersixseven.devicecommandservice.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class InternalApiKeyServerInterceptor implements ServerInterceptor {

  public static final Metadata.Key<String> KEY =
      Metadata.Key.of("x-internal-api-key", Metadata.ASCII_STRING_MARSHALLER);

  private static final String DEVICE_COMMAND_SERVICE = "cybersixseven.device.DeviceCommand/";

  private final byte[] expectedKey;

  public InternalApiKeyServerInterceptor(String expectedKey) {
    if (expectedKey == null || expectedKey.isBlank()) {
      throw new IllegalStateException("app.grpc.internal-api-key must be configured");
    }
    this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    if (!call.getMethodDescriptor().getFullMethodName().startsWith(DEVICE_COMMAND_SERVICE)) {
      return next.startCall(call, headers);
    }
    String provided = headers.get(KEY);
    byte[] actual = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
    if (provided == null || !MessageDigest.isEqual(expectedKey, actual)) {
      call.close(Status.UNAUTHENTICATED.withDescription("invalid internal key"), new Metadata());
      return new ServerCall.Listener<>() {};
    }
    return next.startCall(call, headers);
  }
}
