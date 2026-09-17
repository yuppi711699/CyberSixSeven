package com.cybersixseven.platformapi.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalApiKeyFilterTests {

    @Test
    void rejectsBlankConfiguredKey() {
        assertThrows(IllegalStateException.class, () -> new InternalApiKeyFilter(""));
        assertThrows(IllegalStateException.class, () -> new InternalApiKeyFilter(null));
    }

    @Test
    void constantTimeCompareRejectsWrongLengthKeys() throws Exception {
        InternalApiKeyFilter filter = new InternalApiKeyFilter("expected-key");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(InternalApiKeyFilter.HEADER, "short");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new AssertionError("chain must not continue");
        };

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
    }
}
