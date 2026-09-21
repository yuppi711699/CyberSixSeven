package com.cybersixseven.platformapi.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ProductAvailabilityFilterTests {

    @Test
    void productPathsAreIdentified() {
        assertTrue(ProductAvailabilityFilter.isProductPath("/api/submissions"));
        assertTrue(ProductAvailabilityFilter.isProductPath("/api/questions/x"));
        assertFalse(ProductAvailabilityFilter.isProductPath("/api/auth/login"));
        assertFalse(ProductAvailabilityFilter.isProductPath("/api/csrf"));
        assertFalse(ProductAvailabilityFilter.isProductPath("/actuator/health"));
    }

    @Test
    void disabledProductRoutesReturnUnavailable() throws Exception {
        ProductAvailabilityFilter filter = new ProductAvailabilityFilter(false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/submissions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
        assertTrue(response.getContentAsString().contains("PRODUCT_UNAVAILABLE"));
    }

    @Test
    void enabledProductRoutesContinue() throws Exception {
        ProductAvailabilityFilter filter = new ProductAvailabilityFilter(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/submissions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(200, response.getStatus());
    }
}
