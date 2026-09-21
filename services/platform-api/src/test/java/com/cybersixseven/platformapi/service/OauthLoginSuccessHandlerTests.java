package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cybersixseven.platformapi.entity.UserAccount;
import com.cybersixseven.platformapi.entity.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@ExtendWith(MockitoExtension.class)
class OauthLoginSuccessHandlerTests {

    @Mock
    private OauthUserProvisioningService oauthUserProvisioningService;

    @Mock
    private OauthExchangeService oauthExchangeService;

    private OauthLoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OauthLoginSuccessHandler(
                oauthUserProvisioningService,
                oauthExchangeService,
                "http://localhost:3000/",
                "http://localhost:3001/");
    }

    @Test
    void studentsRedirectToTheStudentCallbackWithAnExchangeCode() throws Exception {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getEmail()).thenReturn("Pat@Example.test");
        when(oidcUser.getGivenName()).thenReturn("Pat");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        UUID userId = UUID.randomUUID();
        when(oauthUserProvisioningService.upsert("pat@example.test", "Pat"))
                .thenReturn(new UserAccount(
                        userId, "pat@example.test", null, "Pat", UserRole.STUDENT, Instant.parse("2026-09-16T00:00:00Z")));
        when(oauthExchangeService.issue(userId, "http://localhost:3000")).thenReturn("one-time");

        HttpServletResponse response = mock(HttpServletResponse.class);
        handler.onAuthenticationSuccess(mock(HttpServletRequest.class), response, authentication);

        verify(response).sendRedirect("http://localhost:3000/auth/callback?code=one-time");
    }

    @Test
    void staffRedirectToTheAdminCallback() throws Exception {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getEmail()).thenReturn("teacher@example.test");
        when(oidcUser.getGivenName()).thenReturn(null);
        when(oidcUser.getFullName()).thenReturn("Ada Teacher");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        UUID userId = UUID.randomUUID();
        when(oauthUserProvisioningService.upsert("teacher@example.test", "Ada Teacher"))
                .thenReturn(new UserAccount(
                        userId,
                        "teacher@example.test",
                        null,
                        "Ada Teacher",
                        UserRole.TEACHER,
                        Instant.parse("2026-09-16T00:00:00Z")));
        when(oauthExchangeService.issue(eq(userId), eq("http://localhost:3001"))).thenReturn("staff-code");

        HttpServletResponse response = mock(HttpServletResponse.class);
        handler.onAuthenticationSuccess(mock(HttpServletRequest.class), response, authentication);

        verify(response).sendRedirect("http://localhost:3001/auth/callback?code=staff-code");
        verify(oauthExchangeService).issue(userId, "http://localhost:3001");
    }

    @Test
    void nicknameFallsBackToTheLocalPart() {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getGivenName()).thenReturn(" ");
        when(oidcUser.getFullName()).thenReturn(null);
        assertEquals("pat", OauthLoginSuccessHandler.nicknameFrom(oidcUser, "pat@example.test"));
        assertEquals("http://localhost:3000", OauthLoginSuccessHandler.stripTrailingSlash("http://localhost:3000/"));
    }
}
