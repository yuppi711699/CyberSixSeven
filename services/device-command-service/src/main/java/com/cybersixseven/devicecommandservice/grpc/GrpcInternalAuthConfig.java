package com.cybersixseven.devicecommandservice.grpc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;

@Configuration
public class GrpcInternalAuthConfig {

  @Bean
  @GlobalServerInterceptor
  InternalApiKeyServerInterceptor internalApiKeyServerInterceptor(
      @Value("${app.grpc.internal-api-key}") String internalApiKey) {
    return new InternalApiKeyServerInterceptor(internalApiKey);
  }
}
