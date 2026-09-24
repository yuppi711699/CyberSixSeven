package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.dto.AdminDeviceResponse;
import com.cybersixseven.platformapi.dto.AdminSubmissionResponse;
import com.cybersixseven.platformapi.entity.Device;
import com.cybersixseven.platformapi.entity.Submission;
import com.cybersixseven.platformapi.entity.UserAccount;
import com.cybersixseven.platformapi.repository.DeviceRepository;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import com.cybersixseven.platformapi.repository.UserAccountRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCatalogService {

    private final SubmissionRepository submissionRepository;
    private final DeviceRepository deviceRepository;
    private final UserAccountRepository userAccountRepository;

    public AdminCatalogService(
            SubmissionRepository submissionRepository,
            DeviceRepository deviceRepository,
            UserAccountRepository userAccountRepository) {
        this.submissionRepository = submissionRepository;
        this.deviceRepository = deviceRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminSubmissionResponse> submissions(UUID studentId, Pageable pageable) {
        Pageable capped = cap(pageable, "createdAt", Set.of("createdAt", "score"));
        Page<Submission> page = studentId == null
                ? submissionRepository.findAll(capped)
                : submissionRepository.findByStudentId(studentId, capped);
        Map<UUID, String> nicknames = nicknames(page.getContent().stream()
                .map(Submission::getStudentId)
                .filter(id -> id != null)
                .toList());
        return page.map(submission -> new AdminSubmissionResponse(
                submission.getId(),
                submission.getStudentId(),
                submission.getStudentId() == null
                        ? ""
                        : nicknames.getOrDefault(submission.getStudentId(), ""),
                submission.getScore(),
                submission.getMaxScore(),
                submission.getAccessoryKey() == null ? "PENDING" : "READY",
                submission.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public Page<AdminDeviceResponse> devices(Pageable pageable) {
        Pageable capped = cap(pageable, "createdAt", Set.of("createdAt", "lastSeenAt", "hardwareId"));
        return deviceRepository.findAll(capped).map(this::toDevice);
    }

    private AdminDeviceResponse toDevice(Device device) {
        return new AdminDeviceResponse(
                device.getId(),
                device.getHardwareId(),
                device.getStudentId(),
                device.isActive(),
                device.getLastSeenAt(),
                device.getCreatedAt());
    }

    private Map<UUID, String> nicknames(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userAccountRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getNickname));
    }

    private static Pageable cap(Pageable pageable, String defaultProperty, Set<String> allowed) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        int page = Math.max(pageable.getPageNumber(), 0);
        Sort sort = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            if (allowed.contains(order.getProperty())) {
                sort = sort.and(Sort.by(order));
            }
        }
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, defaultProperty);
        }
        return PageRequest.of(page, size, sort);
    }
}
