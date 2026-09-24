package com.cybersixseven.reward

const val MIN_INTENSITY = 1
const val MAX_INTENSITY = 5

const val VOICE_TRY_AGAIN = "try-again"
const val VOICE_KEEP_GOING = "keep-going"
const val VOICE_NAILED_IT = "nailed-it"

data class RewardSelection(val intensity: Int, val voiceLineId: String)

data class SelectRewardRequest(val score: Int, val totalQuestions: Int)

data class SelectRewardResponse(val intensity: Int, val voiceLineId: String)
