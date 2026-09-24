package com.cybersixseven.devicecommandservice.reward;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpRewardClient implements RewardClient {

  private static final Logger log = LoggerFactory.getLogger(HttpRewardClient.class);

  private final RestClient restClient;

  public HttpRewardClient(
      @Value("${app.reward.base-url}") String baseUrl,
      @Value("${app.reward.internal-api-key}") String internalApiKey,
      @Value("${app.reward.connect-timeout-ms}") int connectTimeoutMs,
      @Value("${app.reward.read-timeout-ms}") int readTimeoutMs) {
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
  public OptionalInt selectIntensity(int score, int totalQuestions) {
    try {
      RewardResponse body = restClient
          .post()
          .uri("/reward/select")
          .body(new RewardRequest(score, totalQuestions))
          .retrieve()
          .body(RewardResponse.class);
      if (body == null) {
        log.error("reward select empty body score={} totalQuestions={}", score, totalQuestions);
        return OptionalInt.empty();
      }
      log.info(
          "reward selected intensity={} voiceLineId={}",
          body.intensity(),
          body.voiceLineId());
      return OptionalInt.of(body.intensity());
    } catch (RuntimeException ex) {
      log.error("reward select failed score={} totalQuestions={}", score, totalQuestions, ex);
      return OptionalInt.empty();
    }
  }

  private record RewardRequest(int score, int totalQuestions) {}

  private record RewardResponse(int intensity, String voiceLineId) {}
}
