package com.cybersixseven.devicecommandservice.observability;

import java.util.regex.Pattern;

public final class LogRedactor {

    private static final Pattern ASSIGNED = Pattern.compile(
            "(?i)(password|passwd|secret|token|api[-_]?key|capability|cookie)(\\s*[=:]\\s*)(\\S+)");
    private static final Pattern AUTHORIZATION =
            Pattern.compile("(?i)authorization\\s*[:=]\\s*\\S+(?:\\s+\\S+)?");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._\\-+/=]+");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_\\-]+=*\\.[A-Za-z0-9_\\-]+=*\\.[A-Za-z0-9_\\-]+=*");

    private LogRedactor() {}

    public static String redact(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String redacted = AUTHORIZATION.matcher(value).replaceAll("Authorization: [redacted]");
        redacted = ASSIGNED.matcher(redacted).replaceAll("$1$2[redacted]");
        redacted = BEARER.matcher(redacted).replaceAll("Bearer [redacted]");
        return JWT.matcher(redacted).replaceAll("[redacted-jwt]");
    }
}
