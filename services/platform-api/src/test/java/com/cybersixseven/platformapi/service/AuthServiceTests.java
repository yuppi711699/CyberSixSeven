package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cybersixseven.platformapi.dto.LoginRequest;
import com.cybersixseven.platformapi.dto.RegisterRequest;
import com.cybersixseven.platformapi.entity.UserAccount;
import com.cybersixseven.platformapi.entity.UserRole;
import com.cybersixseven.platformapi.repository.UserAccountRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccessTokenService accessTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userAccountRepository, passwordEncoder, accessTokenService, refreshTokenService);
    }

    @Test
    void registerAlwaysCreatesStudentAndIgnoresRequestedRole() {
        when(userAccountRepository.existsByEmail("pat@example.test")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("argon-hash");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubTokens();

        AuthService.IssuedSession session = authService.register(
                new RegisterRequest("Pat@Example.test", "password1", "Pat", "ADMIN"));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(captor.capture());
        assertEquals(UserRole.STUDENT, captor.getValue().getRole());
        assertEquals("pat@example.test", captor.getValue().getEmail());
        assertEquals("argon-hash", captor.getValue().getPasswordHash());
        assertEquals("STUDENT", session.response().user().role());
        assertEquals("pat@example.test", session.response().user().email());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userAccountRepository.existsByEmail("pat@example.test")).thenReturn(true);
        assertThrows(
                DuplicateEmailException.class,
                () -> authService.register(new RegisterRequest("pat@example.test", "password1", "Pat", null)));
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void loginRejectsOauthOnlyAccountsAndBadPasswords() {
        UserAccount oauthOnly = new UserAccount(
                UUID.randomUUID(),
                "pat@example.test",
                null,
                "Pat",
                UserRole.STUDENT,
                Instant.parse("2026-09-16T00:00:00Z"));
        when(userAccountRepository.findByEmail("pat@example.test")).thenReturn(Optional.of(oauthOnly));
        assertThrows(
                AuthUnauthorizedException.class,
                () -> authService.login(new LoginRequest("pat@example.test", "password1")));

        UserAccount withPassword = new UserAccount(
                oauthOnly.getId(),
                oauthOnly.getEmail(),
                "hash",
                "Pat",
                UserRole.STUDENT,
                Instant.parse("2026-09-16T00:00:00Z"));
        when(userAccountRepository.findByEmail("pat@example.test")).thenReturn(Optional.of(withPassword));
        when(passwordEncoder.matches("nope", "hash")).thenReturn(false);
        assertThrows(
                AuthUnauthorizedException.class, () -> authService.login(new LoginRequest("pat@example.test", "nope")));
    }

    @Test
    void requirePasswordEnforcesMinimumLength() {
        assertThrows(InvalidAuthException.class, () -> AuthService.requirePassword("short"));
        assertEquals("password1", AuthService.requirePassword("password1"));
    }

    private void stubTokens() {
        UserAccount anyUser = new UserAccount(
                UUID.randomUUID(), "pat@example.test", "hash", "Pat", UserRole.STUDENT, Instant.now());
        when(accessTokenService.issue(any(UserAccount.class)))
                .thenReturn(new AccessTokenService.IssuedAccessToken("access", 600, Instant.now()));
        when(refreshTokenService.issue(any(UUID.class)))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken(
                        "refresh", anyUser.getId(), UUID.randomUUID(), 1209600));
    }
}
