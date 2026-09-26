package com.cybersixseven.reward

import ch.qos.logback.classic.spi.ILoggingEvent
import org.springframework.boot.json.JsonWriter
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer

class RedactingLogCustomizer : StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
    override fun customize(members: JsonWriter.Members<ILoggingEvent>) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String::class.java, LogRedactor::redact))
    }
}

object LogRedactor {
    private val assigned =
        Regex("(?i)(password|passwd|secret|token|api[-_]?key|capability|cookie)(\\s*[=:]\\s*)(\\S+)")
    private val authorization = Regex("(?i)authorization\\s*[:=]\\s*\\S+(?:\\s+\\S+)?")
    private val bearer = Regex("(?i)bearer\\s+[A-Za-z0-9._\\-+/=]+")
    private val jwt = Regex("eyJ[A-Za-z0-9_\\-]+=*\\.[A-Za-z0-9_\\-]+=*\\.[A-Za-z0-9_\\-]+=*")

    @JvmStatic
    fun redact(value: String?): String? {
        if (value.isNullOrEmpty()) {
            return value
        }
        return value
            .replace(authorization, "Authorization: [redacted]")
            .replace(assigned, "$1$2[redacted]")
            .replace(bearer, "Bearer [redacted]")
            .replace(jwt, "[redacted-jwt]")
    }
}
