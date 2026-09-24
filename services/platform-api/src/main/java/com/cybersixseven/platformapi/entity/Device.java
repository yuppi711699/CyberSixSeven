package com.cybersixseven.platformapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    private UUID id;

    @Column(name = "hardware_id", nullable = false, unique = true)
    private String hardwareId;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Device() {}

    public Device(UUID id, String hardwareId, Instant createdAt) {
        this.id = id;
        this.hardwareId = hardwareId;
        this.studentId = null;
        this.active = true;
        this.lastSeenAt = null;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getHardwareId() {
        return hardwareId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void touchLastSeen(Instant at) {
        this.lastSeenAt = at;
    }
}
