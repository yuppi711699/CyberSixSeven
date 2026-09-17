package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.dto.AccessoryUpdateResponse;
import com.cybersixseven.platformapi.dto.SubmissionDetailResponse;
import com.cybersixseven.platformapi.entity.Submission;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessoryService {

    private final SubmissionRepository submissionRepository;

    public AccessoryService(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    public AccessoryUpdateResponse associate(UUID submissionId, String accessoryKey) {
        if (submissionId == null) {
            throw new IllegalArgumentException("submissionId is required");
        }
        if (!AccessoryKeys.isCanonical(submissionId, accessoryKey)) {
            throw new AccessoryKeyConflictException(submissionId);
        }

        Submission submission = submissionRepository
                .findById(submissionId)
                .orElseThrow(SubmissionNotFoundException::new);
        String current = submission.getAccessoryKey();
        if (current == null) {
            submission.setAccessoryKey(AccessoryKeys.forSubmission(submissionId));
        } else if (!current.equals(accessoryKey)) {
            throw new AccessoryKeyConflictException(submissionId);
        }
        return new AccessoryUpdateResponse(submissionId, SubmissionDetailResponse.READY);
    }
}
