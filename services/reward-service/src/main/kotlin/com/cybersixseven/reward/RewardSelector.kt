package com.cybersixseven.reward

import org.springframework.stereotype.Component

@Component
class RewardSelector(strategies: List<RewardStrategy>) {
    private val encourage = strategies.filterIsInstance<EncourageStrategy>().single()
    private val steady = strategies.filterIsInstance<SteadyStrategy>().single()
    private val celebrate = strategies.filterIsInstance<CelebrateStrategy>().single()

    fun select(score: Int, totalQuestions: Int): RewardSelection {
        require(totalQuestions >= 0) { "totalQuestions must be >= 0" }
        require(score >= 0) { "score must be >= 0" }
        val chosen = when {
            totalQuestions == 0 || score == 0 -> encourage
            score == totalQuestions -> celebrate
            else -> steady
        }
        val raw = when (chosen) {
            is EncourageStrategy -> chosen.select()
            is SteadyStrategy -> chosen.select()
            is CelebrateStrategy -> chosen.select()
        }
        return raw.copy(intensity = raw.intensity.coerceIn(MIN_INTENSITY, MAX_INTENSITY))
    }
}
