package com.cybersixseven.platformapi.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cybersixseven.platformapi.entity.UserAccount;
import com.cybersixseven.platformapi.entity.UserRole;
import com.cybersixseven.platformapi.repository.UserAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class StaffUserFixtureTests {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsTeacherAndAdminOnce() {
        when(userAccountRepository.existsByEmail("teacher@example.test")).thenReturn(false);
        when(userAccountRepository.existsByEmail("admin@example.test")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        StaffUserFixture fixture = new StaffUserFixture(
                userAccountRepository,
                passwordEncoder,
                "teacher@example.test",
                "teacher-pass-1",
                "Teacher",
                "admin@example.test",
                "admin-pass-1",
                "Admin");
        fixture.run(new DefaultApplicationArguments());
        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(UserRole.TEACHER, captor.getAllValues().get(0).getRole());
        assertEquals(UserRole.ADMIN, captor.getAllValues().get(1).getRole());
    }

    @Test
    void skipsExistingEmails() {
        when(userAccountRepository.existsByEmail("teacher@example.test")).thenReturn(true);
        when(userAccountRepository.existsByEmail("admin@example.test")).thenReturn(true);
        StaffUserFixture fixture = new StaffUserFixture(
                userAccountRepository,
                passwordEncoder,
                "teacher@example.test",
                "teacher-pass-1",
                "Teacher",
                "admin@example.test",
                "admin-pass-1",
                "Admin");
        fixture.run(new DefaultApplicationArguments());
        verify(userAccountRepository, never()).save(any());
    }
}
