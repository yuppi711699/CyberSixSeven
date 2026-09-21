package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.entity.Submission;
import com.cybersixseven.platformapi.entity.UserRole;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Service
public class AccessoryDownloadService {

    static final Duration MAX_TTL = Duration.ofMinutes(5);

    private final SubmissionRepository submissionRepository;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration ttl;

    public AccessoryDownloadService(
            SubmissionRepository submissionRepository,
            S3Presigner s3Presigner,
            @Value("${app.aws.s3.bucket}") String bucket,
            @Value("${app.aws.s3.presign-ttl}") Duration ttl) {
        this.submissionRepository = submissionRepository;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.ttl = ttl.compareTo(MAX_TTL) > 0 ? MAX_TTL : ttl;
    }

    @Transactional(readOnly = true)
    public URI presignFor(UUID submissionId, UUID userId, UserRole role) {
        Submission submission = load(submissionId, userId, role);
        if (submission.getAccessoryKey() == null) {
            throw new AccessoryNotReadyException();
        }
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket)
                .key(submission.getAccessoryKey())
                .build();
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(get)
                .build());
        try {
            return presigned.url().toURI();
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalStateException("presigned URL is not a URI", exception);
        }
    }

    Duration ttl() {
        return ttl;
    }

    private Submission load(UUID submissionId, UUID userId, UserRole role) {
        if (submissionId == null || userId == null) {
            throw new SubmissionNotFoundException();
        }
        if (role != null && role.isStaff()) {
            return submissionRepository.findById(submissionId).orElseThrow(SubmissionNotFoundException::new);
        }
        return submissionRepository
                .findByIdAndStudentId(submissionId, userId)
                .orElseThrow(SubmissionNotFoundException::new);
    }
}
