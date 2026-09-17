package com.cybersixseven.platformapi.config;

import com.cybersixseven.platformapi.entity.Device;
import com.cybersixseven.platformapi.repository.DeviceRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
@ConditionalOnProperty(prefix = "app.fixtures", name = "enabled", havingValue = "true")
public class DemoDeviceFixture implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDeviceFixture.class);

    private final DeviceRepository deviceRepository;
    private final String demoDeviceId;

    public DemoDeviceFixture(
            DeviceRepository deviceRepository,
            @Value("${app.devices.demo-device-id}") String demoDeviceId) {
        this.deviceRepository = deviceRepository;
        this.demoDeviceId = demoDeviceId;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (deviceRepository.findByHardwareId(demoDeviceId).isPresent()) {
            return;
        }
        deviceRepository.save(new Device(UUID.randomUUID(), demoDeviceId, Instant.now()));
        log.info("provisioned demo device hardwareId={}", demoDeviceId);
    }
}
