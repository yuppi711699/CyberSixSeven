package com.cybersixseven.platformapi.repository;

import com.cybersixseven.platformapi.entity.OutboxEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {}
