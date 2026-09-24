package com.cybersixseven.platformapi.controller;

import com.cybersixseven.platformapi.dto.DeviceHeartbeatRequest;
import com.cybersixseven.platformapi.dto.DeviceHeartbeatResponse;
import com.cybersixseven.platformapi.service.DeviceHeartbeatService;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/devices")
public class InternalDeviceController {

    private final DeviceHeartbeatService deviceHeartbeatService;

    public InternalDeviceController(DeviceHeartbeatService deviceHeartbeatService) {
        this.deviceHeartbeatService = deviceHeartbeatService;
    }

    @PatchMapping("/{hardwareId}/heartbeat")
    public DeviceHeartbeatResponse heartbeat(
            @PathVariable String hardwareId, @RequestBody DeviceHeartbeatRequest request) {
        return deviceHeartbeatService.record(hardwareId, request);
    }
}
