package com.cybersixseven.platformapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "command_resend_audits")
public class CommandResendAudit {

    @Id
    private UUID id;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "command_id", nullable = false)
    private UUID commandId;

    @Column(nullable = false)
    private String outcome;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CommandResendAudit() {}

    public CommandResendAudit(
            UUID id, UUID staffUserId, UUID deviceId, UUID commandId, String outcome, Instant createdAt) {
        this.id = id;
        this.staffUserId = staffUserId;
        this.deviceId = deviceId;
        this.commandId = commandId;
        this.outcome = outcome;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStaffUserId() {
        return staffUserId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public UUID getCommandId() {
        return commandId;
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
