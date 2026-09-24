package com.cybersixseven.reward

import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestController
class RewardController(private val selector: RewardSelector) {

    @PostMapping("/reward/select")
    fun select(@RequestBody request: SelectRewardRequest): SelectRewardResponse {
        if (request.score < 0 || request.totalQuestions < 0 || request.score > request.totalQuestions) {
            throw InvalidRewardRequestException("score must be between 0 and totalQuestions")
        }
        val selection = selector.select(request.score, request.totalQuestions)
        return SelectRewardResponse(selection.intensity, selection.voiceLineId)
    }
}

class InvalidRewardRequestException(message: String) : RuntimeException(message)

@RestControllerAdvice
class RewardExceptionHandler {

    @ExceptionHandler(InvalidRewardRequestException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalid(exception: InvalidRewardRequestException): Map<String, String> =
        mapOf("code" to "INVALID_REWARD", "message" to (exception.message ?: "invalid reward request"))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun unreadable(): Map<String, String> =
        mapOf("code" to "INVALID_REWARD", "message" to "score and totalQuestions are required")
}
