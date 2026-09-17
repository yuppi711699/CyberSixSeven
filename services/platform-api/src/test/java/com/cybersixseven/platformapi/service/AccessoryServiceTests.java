package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cybersixseven.platformapi.dto.AccessoryUpdateResponse;
import com.cybersixseven.platformapi.dto.SubmissionDetailResponse;
import com.cybersixseven.platformapi.entity.Submission;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessoryServiceTests {

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private AccessoryService accessoryService;

    @Test
    void setsCanonicalKeyWhenNull() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Submission submission = new Submission(id, List.of(), 0, 1, new byte[32], Instant.parse("2026-09-16T00:00:00Z"));
        when(submissionRepository.findById(id)).thenReturn(Optional.of(submission));

        AccessoryUpdateResponse response = accessoryService.associate(id, AccessoryKeys.forSubmission(id));

        assertEquals(id, response.id());
        assertEquals(SubmissionDetailResponse.READY, response.accessoryStatus());
        assertEquals(AccessoryKeys.forSubmission(id), submission.getAccessoryKey());
    }

    @Test
    void repeatingTheCanonicalKeyIsIdempotent() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Submission submission = new Submission(id, List.of(), 0, 1, new byte[32], Instant.parse("2026-09-16T00:00:00Z"));
        submission.setAccessoryKey(AccessoryKeys.forSubmission(id));
        when(submissionRepository.findById(id)).thenReturn(Optional.of(submission));

        AccessoryUpdateResponse response = accessoryService.associate(id, AccessoryKeys.forSubmission(id));

        assertEquals(SubmissionDetailResponse.READY, response.accessoryStatus());
        assertEquals(AccessoryKeys.forSubmission(id), submission.getAccessoryKey());
    }

    @Test
    void rejectsNonCanonicalAndConflictingKeysWithoutWriting() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertThrows(
                AccessoryKeyConflictException.class,
                () -> accessoryService.associate(id, "accessories/" + id + ".stl"));
        assertThrows(
                AccessoryKeyConflictException.class,
                () -> accessoryService.associate(id, "https://example.invalid/" + id));
        verify(submissionRepository, never()).findById(id);

        Submission submission = new Submission(id, List.of(), 0, 1, new byte[32], Instant.parse("2026-09-16T00:00:00Z"));
        submission.setAccessoryKey("accessories/" + id + "/reward.stl");
        when(submissionRepository.findById(id)).thenReturn(Optional.of(submission));
        // Canonical for this id is the same as current; conflict requires a different stored key.
        submission.setAccessoryKey("accessories/00000000-0000-0000-0000-000000000000/reward.stl");
        assertThrows(
                AccessoryKeyConflictException.class,
                () -> accessoryService.associate(id, AccessoryKeys.forSubmission(id)));
    }

    @Test
    void unknownSubmissionIsNotFound() {
        UUID id = UUID.randomUUID();
        when(submissionRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(
                SubmissionNotFoundException.class,
                () -> accessoryService.associate(id, AccessoryKeys.forSubmission(id)));
    }

    @Test
    void nullSubmissionIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> accessoryService.associate(null, "x"));
    }
}
