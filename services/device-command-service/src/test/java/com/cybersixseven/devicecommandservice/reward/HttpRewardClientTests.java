package com.cybersixseven.devicecommandservice.reward;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class HttpRewardClientTests {

  @Test
  void outageReturnsEmptyPromptly() {
    HttpRewardClient client = new HttpRewardClient("http://127.0.0.1:1", "test-internal-api-key", 200, 200);
    long started = System.nanoTime();
    OptionalInt intensity = client.selectIntensity(1, 3);
    long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
    assertTrue(intensity.isEmpty());
    assertTrue(elapsedMs < 1500, "fallback took " + elapsedMs + "ms");
  }

  @Test
  void missingCredentialsAreNotEqualToARealKey() {
    HttpRewardClient client = new HttpRewardClient("http://127.0.0.1:1", "", 200, 200);
    assertFalse(client.selectIntensity(0, 0).isPresent());
  }
}
