package com.cybersixseven.platformapi.controller;

import com.cybersixseven.platformapi.dto.AdminDeviceResponse;
import com.cybersixseven.platformapi.dto.AdminSubmissionResponse;
import com.cybersixseven.platformapi.dto.LeaderboardEntryResponse;
import com.cybersixseven.platformapi.dto.ResendCommandResponse;
import com.cybersixseven.platformapi.service.AdminCatalogService;
import com.cybersixseven.platformapi.service.AuthUnauthorizedException;
import com.cybersixseven.platformapi.service.CommandResendService;
import com.cybersixseven.platformapi.service.LeaderboardService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class AdminCatalogController {

    private final AdminCatalogService adminCatalogService;
    private final LeaderboardService leaderboardService;
    private final CommandResendService commandResendService;

    public AdminCatalogController(
            AdminCatalogService adminCatalogService,
            LeaderboardService leaderboardService,
            CommandResendService commandResendService) {
        this.adminCatalogService = adminCatalogService;
        this.leaderboardService = leaderboardService;
        this.commandResendService = commandResendService;
    }

    @GetMapping("/submissions")
    public Page<AdminSubmissionResponse> submissions(
            @RequestParam(required = false) UUID studentId, Pageable pageable) {
        return adminCatalogService.submissions(studentId, pageable);
    }

    @GetMapping("/devices")
    public Page<AdminDeviceResponse> devices(Pageable pageable) {
        return adminCatalogService.devices(pageable);
    }

    @GetMapping("/leaderboard")
    public Page<LeaderboardEntryResponse> leaderboard(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return leaderboardService.page(page, size);
    }

    @PostMapping("/devices/{id}/resend-command")
    public ResendCommandResponse resend(@PathVariable UUID id) {
        return commandResendService.resend(currentUserId(), id);
    }

    private static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AuthUnauthorizedException("UNAUTHORIZED", "authentication required");
        }
        return UUID.fromString(authentication.getName());
    }
}
