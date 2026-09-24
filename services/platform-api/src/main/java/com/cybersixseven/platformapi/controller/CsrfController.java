package com.cybersixseven.platformapi.controller;

import com.cybersixseven.platformapi.dto.CsrfTokenResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/csrf")
public class CsrfController {

    @GetMapping
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getToken());
    }
}
