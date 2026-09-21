package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.entity.UserAccount;
import com.cybersixseven.platformapi.entity.UserRole;
import com.cybersixseven.platformapi.repository.UserAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OauthUserProvisioningService {

    private final UserAccountRepository userAccountRepository;

    public OauthUserProvisioningService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public UserAccount upsert(String email, String nickname) {
        return findOrCreate(email, nickname, UserRole.STUDENT);
    }

    @Transactional
    public UserAccount findOrCreate(String email, String nickname, UserRole role) {
        return userAccountRepository
                .findByEmail(email)
                .orElseGet(() -> userAccountRepository.save(new UserAccount(
                        UUID.randomUUID(), email, null, nickname, role, Instant.now())));
    }
}
