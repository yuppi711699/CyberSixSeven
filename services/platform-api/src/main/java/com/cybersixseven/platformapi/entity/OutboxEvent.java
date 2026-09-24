package com.cybersixseven.platformapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private DeviceCommandEvent payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "lease_owner")
    private String leaseOwner;

    protected OutboxEvent() {}

    public OutboxEvent(UUID commandId, DeviceCommandEvent payload, Instant createdAt) {
        this.id = commandId;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public DeviceCommandEvent getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public void claim(String owner, Instant until) {
        this.leaseOwner = owner;
        this.leaseUntil = until;
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
        this.leaseOwner = null;
        this.leaseUntil = null;
    }

    public void releaseLease() {
        this.leaseOwner = null;
        this.leaseUntil = null;
    }
}
