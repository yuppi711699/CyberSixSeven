package com.cybersixseven.platformapi.repository;

import com.cybersixseven.platformapi.entity.Device;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByHardwareId(String hardwareId);

    Optional<Device> findByHardwareIdAndActiveTrue(String hardwareId);

    Optional<Device> findFirstByStudentIdAndActiveTrue(UUID studentId);
}
