package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.entity.CommandResendAudit;
import com.cybersixseven.platformapi.repository.CommandResendAuditRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommandResendAuditService {

    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    public static final String RATE_LIMITED = "RATE_LIMITED";

    private final CommandResendAuditRepository repository;

    public CommandResendAuditService(CommandResendAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(UUID staffUserId, UUID deviceId, UUID commandId, String outcome) {
        repository.save(new CommandResendAudit(
                UUID.randomUUID(), staffUserId, deviceId, commandId, outcome, Instant.now()));
    }
}
