package com.cybersixseven.platformapi.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity 
@Table (name = "robots")
public class Robot {
    @Id 
    private UUID id;

    @Column (nullable = false)
    private String name;

    @Column (nullable = false)
    private String model;

    @Column (nullable = false)
    private Instant createdAt;

    protected Robot() {}

    public Robot(UUID id, String name, String model, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.model = model;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getModel() {
        return model;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
}
