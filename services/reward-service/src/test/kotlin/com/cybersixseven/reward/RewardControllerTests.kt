package com.cybersixseven.reward

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["app.internal-api-key=test-internal-api-key"],
)
class RewardControllerTests(@LocalServerPort port: Int) {
    private val client = RestClient.builder().baseUrl("http://127.0.0.1:$port").build()

    @Test
    fun missingKeyIsUnauthorized() {
        val status = post(null, """{"score":1,"totalQuestions":1}""")
        assertEquals(401, status)
    }

    @Test
    fun wrongKeyIsUnauthorized() {
        val status = post("nope", """{"score":1,"totalQuestions":1}""")
        assertEquals(401, status)
    }

    @Test
    fun perfectScoreReturnsCelebrate() {
        val body = client.post()
            .uri("/reward/select")
            .header(InternalApiKeyFilter.HEADER, "test-internal-api-key")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"score":2,"totalQuestions":2}""")
            .retrieve()
            .body(SelectRewardResponse::class.java)
        assertEquals(5, body?.intensity)
        assertEquals(VOICE_NAILED_IT, body?.voiceLineId)
    }

    @Test
    fun scoreAboveTotalIsRejected() {
        val status = post("test-internal-api-key", """{"score":4,"totalQuestions":2}""")
        assertEquals(400, status)
    }

    private fun post(key: String?, json: String): Int {
        return try {
            val spec = client.post()
                .uri("/reward/select")
                .contentType(MediaType.APPLICATION_JSON)
            if (key != null) {
                spec.header(InternalApiKeyFilter.HEADER, key)
            }
            spec.body(json).retrieve().toBodilessEntity().statusCode.value()
        } catch (ex: RestClientResponseException) {
            ex.statusCode.value()
        }
    }
}
