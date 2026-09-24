package com.cybersixseven.platformapi.repository;

import com.cybersixseven.platformapi.entity.CommandResendAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandResendAuditRepository extends JpaRepository<CommandResendAudit, UUID> {}
