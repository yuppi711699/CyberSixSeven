package com.cybersixseven.platformapi.controller;

import com.cybersixseven.platformapi.dto.AccessoryUpdateResponse;
import com.cybersixseven.platformapi.dto.UpdateAccessoryRequest;
import com.cybersixseven.platformapi.service.AccessoryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/submissions")
public class InternalSubmissionController {

    private final AccessoryService accessoryService;

    public InternalSubmissionController(AccessoryService accessoryService) {
        this.accessoryService = accessoryService;
    }

    @PatchMapping("/{id}/accessory")
    public AccessoryUpdateResponse updateAccessory(
            @PathVariable UUID id, @RequestBody UpdateAccessoryRequest request) {
        String accessoryKey = request == null ? null : request.accessoryKey();
        return accessoryService.associate(id, accessoryKey);
    }
}
