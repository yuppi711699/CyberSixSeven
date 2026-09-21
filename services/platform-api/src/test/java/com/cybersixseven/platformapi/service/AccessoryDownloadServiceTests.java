package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cybersixseven.platformapi.entity.Submission;
import com.cybersixseven.platformapi.entity.UserRole;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class AccessoryDownloadServiceTests {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedGetObjectRequest presigned;

    private AccessoryDownloadService service;
    private UUID submissionId;
    private UUID studentId;

    @BeforeEach
    void setUp() throws Exception {
        submissionId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        studentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        service = new AccessoryDownloadService(
                submissionRepository, s3Presigner, "cybersixseven-accessories", Duration.ofMinutes(10));
    }

    private void stubPresign() throws Exception {
        when(presigned.url()).thenReturn(new URL("https://s3.example/object"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
    }

    @Test
    void clampsTtlToFiveMinutesAndUsesOwnerScopedLookup() throws Exception {
        stubPresign();
        assertEquals(Duration.ofMinutes(5), service.ttl());
        Submission stored = new Submission(
                submissionId, studentId, List.of(), 1, 1, new byte[32], Instant.parse("2026-09-16T00:00:00Z"));
        stored.setAccessoryKey(AccessoryKeys.forSubmission(submissionId));
        when(submissionRepository.findByIdAndStudentId(submissionId, studentId)).thenReturn(Optional.of(stored));

        URI uri = service.presignFor(submissionId, studentId, UserRole.STUDENT);
        assertEquals("https://s3.example/object", uri.toString());

        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertEquals(Duration.ofMinutes(5), captor.getValue().signatureDuration());
        verify(submissionRepository, never()).findById(submissionId);
        assertTrue(captor.getValue().getObjectRequest().key().endsWith("reward.stl"));
    }

    @Test
    void staffUsesTheStaffRepositoryPath() throws Exception {
        stubPresign();
        Submission stored = new Submission(
                submissionId, studentId, List.of(), 1, 1, new byte[32], Instant.parse("2026-09-16T00:00:00Z"));
        stored.setAccessoryKey(AccessoryKeys.forSubmission(submissionId));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(stored));

        service.presignFor(submissionId, UUID.randomUUID(), UserRole.TEACHER);
        verify(submissionRepository).findById(submissionId);
        verify(submissionRepository, never()).findByIdAndStudentId(any(), any());
    }

    @Test
    void missingAccessoryIsConflictNotALeakOfTheKey() {
        Submission stored = new Submission(
                submissionId, studentId, List.of(), 1, 1, new byte[32], Instant.parse("2026-09-16T00:00:00Z"));
        when(submissionRepository.findByIdAndStudentId(submissionId, studentId)).thenReturn(Optional.of(stored));
        assertThrows(
                AccessoryNotReadyException.class,
                () -> service.presignFor(submissionId, studentId, UserRole.STUDENT));
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void crossOwnerIsNotFound() {
        when(submissionRepository.findByIdAndStudentId(submissionId, studentId)).thenReturn(Optional.empty());
        assertThrows(
                SubmissionNotFoundException.class,
                () -> service.presignFor(submissionId, studentId, UserRole.STUDENT));
    }
}
