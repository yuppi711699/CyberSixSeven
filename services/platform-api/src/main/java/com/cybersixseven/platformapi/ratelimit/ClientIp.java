package com.cybersixseven.platformapi.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Client address after {@code server.forward-headers-strategy=framework}.
 *
 * <p>{@code ForwardedHeaderFilter} runs before the security chain and copies {@code
 * X-Forwarded-For} into {@link HttpServletRequest#getRemoteAddr()}. Keying the bucket on the
 * socket address behind the ALB collapses every caller into one bucket. When the header is
 * still visible, the rightmost hop is the address the closest proxy appended (ALB), not a
 * leftmost value the client can spoof.
 */
public final class ClientIp {

    private ClientIp() {}

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String candidate = parts[i].trim();
                if (!candidate.isEmpty() && !"unknown".equalsIgnoreCase(candidate)) {
                    return candidate;
                }
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
