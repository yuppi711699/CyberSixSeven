package com.cybersixseven.platformapi.grpc;

import com.cybersixseven.contracts.device.DeviceCommandGrpc;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.grpc.client.GrpcChannelBuilderCustomizer;
import org.springframework.grpc.client.ImportGrpcClients;

@Configuration
@ImportGrpcClients(target = "device-command", types = DeviceCommandGrpc.DeviceCommandBlockingStub.class)
public class DeviceCommandGrpcConfig {

    @Bean
    GrpcChannelBuilderCustomizer<NettyChannelBuilder> deviceCommandConnectTimeout(
            @Value("${app.grpc.device-command.connect-timeout}") Duration connectTimeout) {
        int millis = (int) connectTimeout.toMillis();
        return (channel, builder) -> {
            if ("device-command".equals(channel)) {
                builder.withOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, millis);
            }
        };
    }

    @Bean
    @GlobalClientInterceptor
    InternalApiKeyClientInterceptor internalApiKeyClientInterceptor(
            @Value("${app.internal-api-key}") String internalApiKey) {
        return new InternalApiKeyClientInterceptor(internalApiKey);
    }
}
