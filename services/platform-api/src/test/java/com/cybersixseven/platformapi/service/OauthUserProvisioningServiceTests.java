package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.cybersixseven.platformapi.entity.UserAccount;
import com.cybersixseven.platformapi.entity.UserRole;
import com.cybersixseven.platformapi.repository.UserAccountRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OauthUserProvisioningServiceTests {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Test
    void reusesExistingUsersAndCreatesStudentsByDefault() {
        OauthUserProvisioningService service = new OauthUserProvisioningService(userAccountRepository);
        UserAccount existing = new UserAccount(
                UUID.randomUUID(),
                "pat@example.test",
                null,
                "Pat",
                UserRole.TEACHER,
                Instant.parse("2026-09-16T00:00:00Z"));
        when(userAccountRepository.findByEmail("pat@example.test")).thenReturn(Optional.of(existing));
        assertEquals(UserRole.TEACHER, service.upsert("pat@example.test", "Pat").getRole());
        verify(userAccountRepository).findByEmail("pat@example.test");
    }

    @Test
    void findOrCreatePersistsTheRequestedRoleWhenMissing() {
        OauthUserProvisioningService service = new OauthUserProvisioningService(userAccountRepository);
        when(userAccountRepository.findByEmail("teacher@example.test")).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount created = service.findOrCreate("teacher@example.test", "Ada", UserRole.TEACHER);
        assertEquals(UserRole.TEACHER, created.getRole());
        assertEquals("teacher@example.test", created.getEmail());
        verify(userAccountRepository).save(any(UserAccount.class));
    }
}
