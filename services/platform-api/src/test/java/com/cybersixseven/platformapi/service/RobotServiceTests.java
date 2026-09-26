package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cybersixseven.platformapi.dto.CreateRobotRequest;
import com.cybersixseven.platformapi.dto.RobotResponse;
import com.cybersixseven.platformapi.dto.UpdateRobotRequest;
import com.cybersixseven.platformapi.entity.Robot;
import com.cybersixseven.platformapi.repository.RobotRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RobotServiceTests {

    @Mock
    private RobotRepository robotRepository;

    private RobotService robotService;

    @BeforeEach
    void setUp() {
        robotService = new RobotService(robotRepository);
    }

    @Test
    void listMapsEveryStoredRobot() {
        Instant created = Instant.parse("2024-01-01T00:00:00Z");
        Robot robot = new Robot(UUID.randomUUID(), "alpha", "mk1", created);
        when(robotRepository.findAllByOrderByNameAsc()).thenReturn(List.of(robot));

        List<RobotResponse> listed = robotService.list();

        assertEquals(1, listed.size());
        assertEquals("alpha", listed.get(0).name());
        assertEquals("mk1", listed.get(0).model());
        assertEquals(created, listed.get(0).createdAt());
    }

    @Test
    void getMissingRobotThrows() {
        UUID id = UUID.randomUUID();
        when(robotRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(RobotNotFoundException.class, () -> robotService.get(id));
    }

    @Test
    void createPersistsNameAndModel() {
        when(robotRepository.save(any(Robot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RobotResponse created = robotService.create(new CreateRobotRequest("beta", "mk2"));

        ArgumentCaptor<Robot> captor = ArgumentCaptor.forClass(Robot.class);
        verify(robotRepository).save(captor.capture());
        assertEquals(captor.getValue().getId(), created.id());
        assertEquals("beta", captor.getValue().getName());
        assertEquals("mk2", captor.getValue().getModel());
    }

    @Test
    void updateKeepsIdAndCreatedAt() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2024-06-01T00:00:00Z");
        when(robotRepository.findById(id)).thenReturn(Optional.of(new Robot(id, "old", "mk1", created)));
        when(robotRepository.save(any(Robot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RobotResponse updated = robotService.update(id, new UpdateRobotRequest("new", "mk9"));

        assertEquals(id, updated.id());
        assertEquals("new", updated.name());
        assertEquals("mk9", updated.model());
        assertEquals(created, updated.createdAt());
    }

    @Test
    void deleteMissingRobotDoesNotDelete() {
        UUID id = UUID.randomUUID();
        when(robotRepository.existsById(id)).thenReturn(false);
        assertThrows(RobotNotFoundException.class, () -> robotService.delete(id));
        verify(robotRepository, never()).deleteById(id);
    }

    @Test
    void deleteExistingRobotRemovesTheRow() {
        UUID id = UUID.randomUUID();
        when(robotRepository.existsById(id)).thenReturn(true);
        robotService.delete(id);
        verify(robotRepository).deleteById(id);
    }
}
