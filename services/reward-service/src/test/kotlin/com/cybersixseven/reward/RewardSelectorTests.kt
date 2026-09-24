package com.cybersixseven.reward

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class RewardSelectorTests {

    private val selector = RewardSelector(
        listOf(EncourageStrategy(), SteadyStrategy(), CelebrateStrategy()),
    )

    @ParameterizedTest
    @CsvSource(
        "0,0,1,try-again",
        "0,3,1,try-again",
        "1,3,3,keep-going",
        "2,3,3,keep-going",
        "3,3,5,nailed-it",
    )
    fun bands(score: Int, totalQuestions: Int, intensity: Int, voiceLineId: String) {
        val selection = selector.select(score, totalQuestions)
        assertEquals(intensity, selection.intensity)
        assertEquals(voiceLineId, selection.voiceLineId)
    }

    @Test
    fun dispatchesToTheChosenStrategy() {
        val encourage = mockk<EncourageStrategy>()
        every { encourage.select() } returns RewardSelection(1, VOICE_TRY_AGAIN)
        val dispatched = RewardSelector(listOf(encourage, SteadyStrategy(), CelebrateStrategy()))

        val selection = dispatched.select(0, 4)

        assertEquals(1, selection.intensity)
        verify { encourage.select() }
    }
}
