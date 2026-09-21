package com.cybersixseven.platformapi.controller;

import com.cybersixseven.platformapi.dto.ClaimResponse;
import com.cybersixseven.platformapi.dto.CreateSubmissionRequest;
import com.cybersixseven.platformapi.dto.CreateSubmissionResponse;
import com.cybersixseven.platformapi.dto.SubmissionDetailResponse;
import com.cybersixseven.platformapi.entity.UserRole;
import com.cybersixseven.platformapi.service.AccessoryDownloadService;
import com.cybersixseven.platformapi.service.AuthUnauthorizedException;
import com.cybersixseven.platformapi.service.SubmissionService;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    public static final String SECRET_HEADER = "X-Submission-Secret";

    private final SubmissionService submissionService;
    private final AccessoryDownloadService accessoryDownloadService;

    public SubmissionController(
            SubmissionService submissionService, AccessoryDownloadService accessoryDownloadService) {
        this.submissionService = submissionService;
        this.accessoryDownloadService = accessoryDownloadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSubmissionResponse createSubmission(
            @RequestBody CreateSubmissionRequest request, @AuthenticationPrincipal Jwt jwt) {
        return submissionService.submit(request, userId(jwt));
    }

    @GetMapping("/{id}")
    public SubmissionDetailResponse getSubmission(
            @PathVariable UUID id,
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt != null) {
            return submissionService.getForOwner(id, userId(jwt));
        }
        return submissionService.getByCapability(id, secret);
    }

    @PostMapping("/{id}/claim")
    public ClaimResponse claim(
            @PathVariable UUID id,
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @AuthenticationPrincipal Jwt jwt) {
        return submissionService.claim(id, userId(jwt), secret);
    }

    @GetMapping("/{id}/accessory/download")
    public ResponseEntity<Void> download(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        URI location = accessoryDownloadService.presignFor(id, userId(jwt), role(jwt));
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    private static UUID userId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AuthUnauthorizedException("UNAUTHORIZED", "authentication required");
        }
        return UUID.fromString(jwt.getSubject());
    }

    private static UserRole role(Jwt jwt) {
        return UserRole.valueOf(jwt.getClaimAsString("role"));
    }
}
