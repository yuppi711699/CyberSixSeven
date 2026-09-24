package com.cybersixseven.platformapi.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

public final class InternalApiKeyClientInterceptor implements ClientInterceptor {

    public static final Metadata.Key<String> KEY =
            Metadata.Key.of("x-internal-api-key", Metadata.ASCII_STRING_MARSHALLER);

    private final String internalApiKey;

    public InternalApiKeyClientInterceptor(String internalApiKey) {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            throw new IllegalStateException("app.internal-api-key must be configured");
        }
        this.internalApiKey = internalApiKey;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(KEY, internalApiKey);
                super.start(responseListener, headers);
            }
        };
    }
}
