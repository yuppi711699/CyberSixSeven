package com.cybersixseven.platformapi.controller;

import com.cybersixseven.platformapi.entity.UserAccount;
import com.cybersixseven.platformapi.entity.UserRole;
import com.cybersixseven.platformapi.service.Emails;
import com.cybersixseven.platformapi.service.OauthExchangeService;
import com.cybersixseven.platformapi.service.OauthUserProvisioningService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!prod")
@ConditionalOnProperty(prefix = "app.oauth.fixture", name = "enabled", havingValue = "true")
@RequestMapping("/api/auth/oauth/fixture")
public class OauthFixtureController {

    private final OauthUserProvisioningService oauthUserProvisioningService;
    private final OauthExchangeService oauthExchangeService;
    private final String studentOrigin;
    private final String adminOrigin;

    public OauthFixtureController(
            OauthUserProvisioningService oauthUserProvisioningService,
            OauthExchangeService oauthExchangeService,
            @Value("${app.frontends.student-origin}") String studentOrigin,
            @Value("${app.frontends.admin-origin}") String adminOrigin) {
        this.oauthUserProvisioningService = oauthUserProvisioningService;
        this.oauthExchangeService = oauthExchangeService;
        this.studentOrigin = studentOrigin;
        this.adminOrigin = adminOrigin;
    }

    @GetMapping
    public void redirect(@RequestParam("role") UserRole role, HttpServletResponse response) throws IOException {
        String email = "oauth-" + role.name().toLowerCase() + "@example.test";
        String nickname = role.name();
        UserAccount user =
                oauthUserProvisioningService.findOrCreate(Emails.normalize(email), nickname, role);
        String frontend = role.isStaff() ? adminOrigin : studentOrigin;
        String code = oauthExchangeService.issue(user.getId(), frontend);
        response.sendRedirect(frontend + "/auth/callback?code=" + code);
    }
}
