package com.cybersixseven.platformapi.config;

import com.cybersixseven.platformapi.entity.UserAccount;
import com.cybersixseven.platformapi.entity.UserRole;
import com.cybersixseven.platformapi.repository.UserAccountRepository;
import com.cybersixseven.platformapi.service.Emails;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
@ConditionalOnProperty(prefix = "app.fixtures", name = "enabled", havingValue = "true")
public class StaffUserFixture implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffUserFixture.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String teacherEmail;
    private final String teacherPassword;
    private final String teacherNickname;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminNickname;

    public StaffUserFixture(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.fixtures.teacher-email}") String teacherEmail,
            @Value("${app.fixtures.teacher-password}") String teacherPassword,
            @Value("${app.fixtures.teacher-nickname}") String teacherNickname,
            @Value("${app.fixtures.admin-email}") String adminEmail,
            @Value("${app.fixtures.admin-password}") String adminPassword,
            @Value("${app.fixtures.admin-nickname}") String adminNickname) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.teacherEmail = teacherEmail;
        this.teacherPassword = teacherPassword;
        this.teacherNickname = teacherNickname;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminNickname = adminNickname;
    }

    @Override
    public void run(ApplicationArguments args) {
        provision(teacherEmail, teacherPassword, teacherNickname, UserRole.TEACHER);
        provision(adminEmail, adminPassword, adminNickname, UserRole.ADMIN);
    }

    private void provision(String email, String password, String nickname, UserRole role) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        String normalized = Emails.normalize(email);
        if (userAccountRepository.existsByEmail(normalized)) {
            return;
        }
        userAccountRepository.save(new UserAccount(
                UUID.randomUUID(),
                normalized,
                passwordEncoder.encode(password),
                nickname,
                role,
                Instant.now()));
        log.info("provisioned fixture user role={}", role);
    }
}
