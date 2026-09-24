package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.dto.AuthResponse;
import com.cybersixseven.platformapi.dto.AuthUserResponse;
import com.cybersixseven.platformapi.dto.LoginRequest;
import com.cybersixseven.platformapi.dto.RegisterRequest;
import com.cybersixseven.platformapi.entity.UserAccount;
import com.cybersixseven.platformapi.entity.UserRole;
import com.cybersixseven.platformapi.repository.UserAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final int MIN_PASSWORD_LENGTH = 8;

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenService accessTokenService,
            RefreshTokenService refreshTokenService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public IssuedSession register(RegisterRequest request) {
        if (request == null) {
            throw new InvalidAuthException("registration body is required");
        }
        String email = Emails.normalize(request.email());
        String nickname = requireNickname(request.nickname());
        String password = requirePassword(request.password());
        if (userAccountRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(password),
                nickname,
                UserRole.STUDENT,
                Instant.now());
        userAccountRepository.save(user);
        return issueSession(user);
    }

    @Transactional(readOnly = true)
    public IssuedSession login(LoginRequest request) {
        if (request == null) {
            throw new InvalidAuthException("login body is required");
        }
        String email = Emails.normalize(request.email());
        String password = request.password() == null ? "" : request.password();
        UserAccount user = userAccountRepository
                .findByEmail(email)
                .orElseThrow(() -> new AuthUnauthorizedException("UNAUTHORIZED", "invalid credentials"));
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthUnauthorizedException("UNAUTHORIZED", "invalid credentials");
        }
        return issueSession(user);
    }

    @Transactional(readOnly = true)
    public IssuedSession refresh(String presentedRefreshToken) {
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.rotate(presentedRefreshToken);
        UserAccount user = userAccountRepository
                .findById(refresh.userId())
                .orElseThrow(() -> new AuthUnauthorizedException("UNAUTHORIZED", "refresh token invalid"));
        return tokensFor(user, refresh);
    }

    @Transactional(readOnly = true)
    public IssuedSession exchange(UUID userId) {
        UserAccount user = userAccountRepository
                .findById(userId)
                .orElseThrow(() -> new AuthUnauthorizedException("OAUTH_EXCHANGE_INVALID", "exchange code invalid"));
        return issueSession(user);
    }

    public void logout(String presentedRefreshToken) {
        refreshTokenService.revokePresented(presentedRefreshToken);
    }

    public IssuedSession issueSession(UserAccount user) {
        return tokensFor(user, refreshTokenService.issue(user.getId()));
    }

    private IssuedSession tokensFor(UserAccount user, RefreshTokenService.IssuedRefreshToken refresh) {
        AccessTokenService.IssuedAccessToken access = accessTokenService.issue(user);
        return new IssuedSession(
                new AuthResponse(
                        access.token(),
                        access.expiresInSeconds(),
                        new AuthUserResponse(
                                user.getId(), user.getEmail(), user.getNickname(), user.getRole().name())),
                refresh.token(),
                refresh.ttlSeconds());
    }

    static String requireNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new InvalidAuthException("nickname is required");
        }
        return nickname.trim();
    }

    static String requirePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidAuthException("password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        return password;
    }

    public record IssuedSession(AuthResponse response, String refreshToken, long refreshTtlSeconds) {}
}
