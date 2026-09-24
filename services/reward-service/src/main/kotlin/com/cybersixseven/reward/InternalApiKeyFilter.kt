package com.cybersixseven.reward

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class InternalApiKeyFilter(expectedKey: String) : OncePerRequestFilter() {
    private val expectedKey: ByteArray = expectedKey.toByteArray(StandardCharsets.UTF_8)

    init {
        if (expectedKey.isBlank()) {
            throw IllegalStateException("app.internal-api-key must be configured")
        }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val provided = request.getHeader(HEADER)
        val actual = provided?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        if (provided == null || !MessageDigest.isEqual(expectedKey, actual)) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"code":"UNAUTHORIZED","message":"invalid internal key"}""")
            return
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val HEADER = "X-Internal-Api-Key"
    }
}

@Configuration
class InternalAuthConfig(
    @param:Value("\${app.internal-api-key}") private val internalApiKey: String,
) {
    @Bean
    fun internalApiKeyFilter(): FilterRegistrationBean<InternalApiKeyFilter> {
        val registration = FilterRegistrationBean(InternalApiKeyFilter(internalApiKey))
        registration.order = Ordered.HIGHEST_PRECEDENCE
        return registration
    }
}
