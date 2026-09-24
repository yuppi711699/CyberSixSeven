package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.cybersixseven.platformapi.dto.DeviceHeartbeatRequest;
import com.cybersixseven.platformapi.dto.DeviceHeartbeatResponse;
import com.cybersixseven.platformapi.entity.Device;
import com.cybersixseven.platformapi.repository.DeviceRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceHeartbeatServiceTests {

    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private DeviceHeartbeatService service;

    @Test
    void rejectsMismatchedDeviceId() {
        assertThrows(
                InvalidHeartbeatException.class,
                () -> service.record(
                        "esp32-dev-001",
                        new DeviceHeartbeatRequest("other", "s", "c", "handled")));
    }

    @Test
    void rejectsPartialAck() {
        InvalidHeartbeatException ex = assertThrows(
                InvalidHeartbeatException.class,
                () -> service.record(
                        "esp32-dev-001",
                        new DeviceHeartbeatRequest("esp32-dev-001", null, "c", "handled")));
        assertEquals(
                "acknowledgements require deviceId, submissionId, commandId, and status",
                ex.getMessage());
    }

    @Test
    void recordsLastSeenForAKnownDevice() {
        Device device = new Device(UUID.randomUUID(), "esp32-dev-001", Instant.parse("2026-09-16T00:00:00Z"));
        when(deviceRepository.findByHardwareId("esp32-dev-001")).thenReturn(Optional.of(device));

        DeviceHeartbeatResponse response = service.record(
                "esp32-dev-001",
                new DeviceHeartbeatRequest(
                        "esp32-dev-001",
                        "22222222-2222-2222-2222-222222222222",
                        "11111111-1111-1111-1111-111111111111",
                        "handled"));

        assertEquals("esp32-dev-001", response.deviceId());
        assertNotNull(response.lastSeenAt());
        assertEquals(response.lastSeenAt(), device.getLastSeenAt());
    }

    @Test
    void unknownDeviceIsNotFound() {
        when(deviceRepository.findByHardwareId("missing")).thenReturn(Optional.empty());
        assertThrows(
                DeviceNotFoundException.class,
                () -> service.record(
                        "missing",
                        new DeviceHeartbeatRequest("missing", null, null, null)));
    }
}
