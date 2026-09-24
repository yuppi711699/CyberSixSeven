package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.entity.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OauthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OauthUserProvisioningService oauthUserProvisioningService;
    private final OauthExchangeService oauthExchangeService;
    private final String studentOrigin;
    private final String adminOrigin;

    public OauthLoginSuccessHandler(
            OauthUserProvisioningService oauthUserProvisioningService,
            OauthExchangeService oauthExchangeService,
            @Value("${app.frontends.student-origin}") String studentOrigin,
            @Value("${app.frontends.admin-origin}") String adminOrigin) {
        this.oauthUserProvisioningService = oauthUserProvisioningService;
        this.oauthExchangeService = oauthExchangeService;
        this.studentOrigin = stripTrailingSlash(studentOrigin);
        this.adminOrigin = stripTrailingSlash(adminOrigin);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            response.sendRedirect(studentOrigin + "/login?error=oauth");
            return;
        }
        String email;
        try {
            email = Emails.normalize(oidcUser.getEmail());
        } catch (InvalidAuthException exception) {
            response.sendRedirect(studentOrigin + "/login?error=oauth");
            return;
        }
        UserAccount user = oauthUserProvisioningService.upsert(email, nicknameFrom(oidcUser, email));
        String frontend = user.getRole().isStaff() ? adminOrigin : studentOrigin;
        String code = oauthExchangeService.issue(user.getId(), frontend);
        response.sendRedirect(frontend + "/auth/callback?code=" + code);
    }

    static String nicknameFrom(OidcUser oidcUser, String email) {
        String name = oidcUser.getGivenName();
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        String full = oidcUser.getFullName();
        if (full != null && !full.isBlank()) {
            return full.trim();
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    static String stripTrailingSlash(String origin) {
        if (origin.endsWith("/")) {
            return origin.substring(0, origin.length() - 1);
        }
        return origin;
    }
}
