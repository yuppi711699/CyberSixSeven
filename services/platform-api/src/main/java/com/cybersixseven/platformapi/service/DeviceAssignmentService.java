package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.entity.Device;
import com.cybersixseven.platformapi.repository.DeviceRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeviceAssignmentService {

    private final DeviceRepository deviceRepository;
    private final String demoDeviceId;

    public DeviceAssignmentService(
            DeviceRepository deviceRepository,
            @Value("${app.devices.demo-device-id}") String demoDeviceId) {
        this.deviceRepository = deviceRepository;
        this.demoDeviceId = demoDeviceId;
    }

    public Device requireAssignedDevice() {
        return requireAssignedDevice(null);
    }

    public Device requireAssignedDevice(UUID studentId) {
        if (studentId != null) {
            var assigned = deviceRepository.findFirstByStudentIdAndActiveTrue(studentId);
            if (assigned.isPresent()) {
                return assigned.get();
            }
        }
        return deviceRepository
                .findByHardwareIdAndActiveTrue(demoDeviceId)
                .orElseThrow(() -> new DeviceNotProvisionedException(
                        "no active device assigned; demo device " + demoDeviceId + " is not provisioned"));
    }
}
