package com.cybersixseven.platformapi.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cybersixseven.platformapi.entity.Device;
import com.cybersixseven.platformapi.repository.DeviceRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class DemoDeviceFixtureTests {

    @Mock
    private DeviceRepository deviceRepository;

    @Test
    void provisionsTheDemoDeviceOnce() {
        when(deviceRepository.findByHardwareId("esp32-dev-001")).thenReturn(Optional.empty());
        DemoDeviceFixture fixture = new DemoDeviceFixture(deviceRepository, "esp32-dev-001");
        fixture.run(new DefaultApplicationArguments());
        verify(deviceRepository).save(any(Device.class));
    }

    @Test
    void skipsWhenAlreadyProvisioned() {
        Device existing = new Device(UUID.randomUUID(), "esp32-dev-001", Instant.parse("2026-09-16T00:00:00Z"));
        when(deviceRepository.findByHardwareId("esp32-dev-001")).thenReturn(Optional.of(existing));
        DemoDeviceFixture fixture = new DemoDeviceFixture(deviceRepository, "esp32-dev-001");
        fixture.run(new DefaultApplicationArguments());
        verify(deviceRepository, never()).save(any(Device.class));
    }
}
