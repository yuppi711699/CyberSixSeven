package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.dto.DeviceHeartbeatRequest;
import com.cybersixseven.platformapi.dto.DeviceHeartbeatResponse;
import com.cybersixseven.platformapi.entity.Device;
import com.cybersixseven.platformapi.repository.DeviceRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceHeartbeatService {

    private final DeviceRepository deviceRepository;

    public DeviceHeartbeatService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public DeviceHeartbeatResponse record(String hardwareId, DeviceHeartbeatRequest request) {
        if (hardwareId == null || hardwareId.isBlank()) {
            throw new InvalidHeartbeatException("device id is required");
        }
        if (request == null) {
            throw new InvalidHeartbeatException("heartbeat body is required");
        }
        if (request.deviceId() != null && !hardwareId.equals(request.deviceId())) {
            throw new InvalidHeartbeatException("deviceId must match the path");
        }
        boolean ack = request.commandId() != null
                || request.submissionId() != null
                || request.status() != null;
        if (ack) {
            if (isBlank(request.commandId())
                    || isBlank(request.submissionId())
                    || isBlank(request.status())
                    || isBlank(request.deviceId())) {
                throw new InvalidHeartbeatException(
                        "acknowledgements require deviceId, submissionId, commandId, and status");
            }
        }

        Device device = deviceRepository
                .findByHardwareId(hardwareId)
                .orElseThrow(() -> new DeviceNotFoundException(hardwareId));
        Instant seenAt = Instant.now();
        device.touchLastSeen(seenAt);
        return new DeviceHeartbeatResponse(device.getHardwareId(), seenAt);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
