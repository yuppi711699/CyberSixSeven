package com.cybersixseven.devicecommandservice.reward;

import java.util.OptionalInt;

public interface RewardClient {

  /**
   * @return selected intensity, or empty when reward-service times out or fails
   */
  OptionalInt selectIntensity(int score, int totalQuestions);
}
