package com.cybersixseven.devicecommandservice.heartbeat;

import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestPlatformHeartbeatClient implements PlatformHeartbeatClient {

  private static final Logger log = LoggerFactory.getLogger(RestPlatformHeartbeatClient.class);

  private final RestClient restClient;

  public RestPlatformHeartbeatClient(
      @Value("${app.platform-api.base-url}") String baseUrl,
      @Value("${app.platform-api.internal-api-key}") String internalApiKey,
      @Value("${app.platform-api.connect-timeout-ms}") int connectTimeoutMs,
      @Value("${app.platform-api.read-timeout-ms}") int readTimeoutMs) {
    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
        .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .defaultHeader("X-Internal-Api-Key", internalApiKey)
        .build();
  }

  @Override
  public void report(String deviceId, String submissionId, String commandId, String status) {
    try {
      restClient
          .patch()
          .uri("/internal/devices/{id}/heartbeat", deviceId)
          .body(new HeartbeatBody(deviceId, submissionId, commandId, status))
          .retrieve()
          .toBodilessEntity();
    } catch (RuntimeException ex) {
      log.error(
          "heartbeat failed deviceId={} submissionId={} commandId={}",
          deviceId,
          submissionId,
          commandId,
          ex);
    }
  }

  private record HeartbeatBody(
      String deviceId, String submissionId, String commandId, String status) {}
}
