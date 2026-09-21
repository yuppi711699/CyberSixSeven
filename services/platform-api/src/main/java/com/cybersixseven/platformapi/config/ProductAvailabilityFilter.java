package com.cybersixseven.platformapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

final class ProductAvailabilityFilter extends OncePerRequestFilter {

    private final boolean productEnabled;

    ProductAvailabilityFilter(boolean productEnabled) {
        this.productEnabled = productEnabled;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!productEnabled && isProductPath(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter()
                    .write(
                            "{\"code\":\"PRODUCT_UNAVAILABLE\",\"message\":\"product routes are disabled until v0.8\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    static boolean isProductPath(String uri) {
        return uri.startsWith("/api/questions")
                || uri.startsWith("/api/submissions")
                || uri.startsWith("/api/admin")
                || uri.startsWith("/api/robots");
    }
}
