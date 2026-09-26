package com.cybersixseven.platformapi.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Not a {@code @Component} and not a {@code @Bean}. Boot registers every {@code Filter} bean
 * on the servlet container, which would also rate-limit {@code /internal/**}.
 */
public final class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final Pattern CLAIM = Pattern.compile("/api/submissions/[^/]+/claim");

    private final RateLimitGates gates;

    public ApiRateLimitFilter(RateLimitGates gates) {
        this.gates = gates;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (limited(request) && !allowed(request)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"too many requests\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean allowed(HttpServletRequest request) {
        String ip = ClientIp.resolve(request);
        if (authPath(request)) {
            return gates.authByIp().tryConsume(ip);
        }
        if (!gates.submissionByIp().tryConsume(ip)) {
            return false;
        }
        String user = authenticatedSubject();
        return user == null || gates.submissionByUser().tryConsume(user);
    }

    private static boolean limited(HttpServletRequest request) {
        return authPath(request) || submissionPath(request);
    }

    private static boolean authPath(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return "/api/auth/register".equals(path)
                || "/api/auth/login".equals(path)
                || "/api/auth/refresh".equals(path)
                || "/api/auth/logout".equals(path)
                || "/api/auth/oauth/exchange".equals(path)
                || path.startsWith("/api/auth/oauth/fixture");
    }

    private static boolean submissionPath(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return "/api/submissions".equals(path) || CLAIM.matcher(path).matches();
    }

    private static String authenticatedSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            return jwt.getSubject();
        }
        return null;
    }
}
