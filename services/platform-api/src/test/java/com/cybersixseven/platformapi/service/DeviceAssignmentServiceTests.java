package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.cybersixseven.platformapi.entity.Device;
import com.cybersixseven.platformapi.repository.DeviceRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceAssignmentServiceTests {

    @Mock
    private DeviceRepository deviceRepository;

    private DeviceAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new DeviceAssignmentService(deviceRepository, "esp32-dev-001");
    }

    @Test
    void returnsTheConfiguredActiveDemoDevice() {
        Device device = new Device(UUID.randomUUID(), "esp32-dev-001", Instant.parse("2026-09-16T00:00:00Z"));
        when(deviceRepository.findByHardwareIdAndActiveTrue("esp32-dev-001")).thenReturn(Optional.of(device));

        assertEquals(device, service.requireAssignedDevice());
    }

    @Test
    void prefersTheStudentsAssignedDevice() {
        UUID studentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Device assigned = new Device(UUID.randomUUID(), "esp32-student", Instant.parse("2026-09-16T00:00:00Z"));
        when(deviceRepository.findFirstByStudentIdAndActiveTrue(studentId)).thenReturn(Optional.of(assigned));

        assertEquals(assigned, service.requireAssignedDevice(studentId));
    }

    @Test
    void failsWhenTheDemoDeviceIsMissing() {
        when(deviceRepository.findByHardwareIdAndActiveTrue("esp32-dev-001")).thenReturn(Optional.empty());
        assertThrows(DeviceNotProvisionedException.class, service::requireAssignedDevice);
    }
}
