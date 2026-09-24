package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.dto.CreateRobotRequest;
import com.cybersixseven.platformapi.dto.RobotResponse;
import com.cybersixseven.platformapi.dto.UpdateRobotRequest;
import com.cybersixseven.platformapi.entity.Robot;
import com.cybersixseven.platformapi.repository.RobotRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RobotService {

    private final RobotRepository robotRepository;

    public RobotService(RobotRepository robotRepository) {
        this.robotRepository = robotRepository;
    }

    @Transactional(readOnly = true)
    public List<RobotResponse> list() {
        return robotRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RobotResponse get(UUID id) {
        return toResponse(requireRobot(id));
    }

    @Transactional
    public RobotResponse create(CreateRobotRequest request) {
        Robot robot = new Robot(
                UUID.randomUUID(), request.name(), request.model(), Instant.now());
        return toResponse(robotRepository.save(robot));
    }

    @Transactional
    public RobotResponse update(UUID id, UpdateRobotRequest request) {
        Robot existing = requireRobot(id);
        Robot updated = new Robot(
                existing.getId(), request.name(), request.model(), existing.getCreatedAt());
        return toResponse(robotRepository.save(updated));
    }

    @Transactional
    public void delete(UUID id) {
        if (!robotRepository.existsById(id)) {
            throw new RobotNotFoundException(id);
        }
        robotRepository.deleteById(id);
    }

    private Robot requireRobot(UUID id) {
        return robotRepository
                .findById(id)
                .orElseThrow(() -> new RobotNotFoundException(id));
    }

    private RobotResponse toResponse(Robot robot) {
        return new RobotResponse(
                robot.getId(), robot.getName(), robot.getModel(), robot.getCreatedAt());
    }
}
