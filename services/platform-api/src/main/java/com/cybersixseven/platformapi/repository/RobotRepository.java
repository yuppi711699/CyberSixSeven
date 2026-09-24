package com.cybersixseven.platformapi.repository;

import com.cybersixseven.platformapi.entity.Robot;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotRepository extends JpaRepository<Robot, UUID> {

    List<Robot> findAllByOrderByNameAsc();
}
