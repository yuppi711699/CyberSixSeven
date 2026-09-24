package com.cybersixseven.reward

import org.springframework.stereotype.Component

sealed interface RewardStrategy {
    fun select(): RewardSelection
}

@Component
class EncourageStrategy : RewardStrategy {
    override fun select() = RewardSelection(MIN_INTENSITY, VOICE_TRY_AGAIN)
}

@Component
class SteadyStrategy : RewardStrategy {
    override fun select() = RewardSelection(3, VOICE_KEEP_GOING)
}

@Component
class CelebrateStrategy : RewardStrategy {
    override fun select() = RewardSelection(MAX_INTENSITY, VOICE_NAILED_IT)
}
