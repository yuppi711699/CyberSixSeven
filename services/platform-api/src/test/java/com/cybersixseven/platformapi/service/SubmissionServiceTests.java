package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cybersixseven.platformapi.dto.AnswerSubmissionRequest;
import com.cybersixseven.platformapi.dto.CreateSubmissionRequest;
import com.cybersixseven.platformapi.dto.CreateSubmissionResponse;
import com.cybersixseven.platformapi.dto.SubmissionDetailResponse;
import com.cybersixseven.platformapi.entity.Device;
import com.cybersixseven.platformapi.entity.OutboxEvent;
import com.cybersixseven.platformapi.entity.Question;
import com.cybersixseven.platformapi.entity.Submission;
import com.cybersixseven.platformapi.event.OutboxCommittedEvent;
import com.cybersixseven.platformapi.repository.OutboxEventRepository;
import com.cybersixseven.platformapi.repository.QuestionRepository;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import com.cybersixseven.platformapi.service.SubmissionCapabilityGenerator.GeneratedCapability;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTests {

    private static final UUID FIRST_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THIRD_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private DeviceAssignmentService deviceAssignmentService;

    @Mock
    private SubmissionCapabilityGenerator capabilityGenerator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionService = new SubmissionService(
                questionRepository,
                submissionRepository,
                outboxEventRepository,
                deviceAssignmentService,
                capabilityGenerator,
                eventPublisher,
                3);
    }

    private void givenQuestions() {
        when(questionRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(questions());
    }

    @Test
    void scoresFromStoredQuestionsAndPersistsAnImmutableSnapshotAndOutbox() {
        givenQuestions();
        when(capabilityGenerator.generate())
                .thenReturn(new GeneratedCapability("plain-capability", new byte[32]));
        when(submissionRepository.save(any(Submission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Device device = new Device(UUID.randomUUID(), "esp32-dev-001", Instant.parse("2026-09-16T00:00:00Z"));
        when(deviceAssignmentService.requireAssignedDevice()).thenReturn(device);

        CreateSubmissionResponse response = submissionService.submit(new CreateSubmissionRequest(
                List.of(
                        new AnswerSubmissionRequest(FIRST_ID, 12),
                        new AnswerSubmissionRequest(SECOND_ID, 0),
                        new AnswerSubmissionRequest(THIRD_ID, 12))));

        assertEquals(2, response.score());
        assertEquals(3, response.maxScore());
        assertEquals("plain-capability", response.submissionSecret());
        assertTrue(response.answers().get(0).correct());
        assertEquals(0, response.answers().get(1).awardedPoints());

        ArgumentCaptor<Submission> submissionCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(submissionCaptor.capture());
        Submission stored = submissionCaptor.getValue();
        assertEquals(2, stored.getScore());
        assertEquals("What is 7 + 5?", stored.getAnswers().get(0).prompt());
        assertEquals(12, stored.getAnswers().get(0).correctAnswer());

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();
        assertEquals(stored.getId(), outbox.getPayload().submissionId());
        assertEquals(outbox.getId(), outbox.getPayload().commandId());
        assertEquals("esp32-dev-001", outbox.getPayload().deviceId());
        assertEquals("incorrect", outbox.getPayload().event());
        assertEquals(3, outbox.getPayload().intensity());
        verify(eventPublisher).publishEvent(any(OutboxCommittedEvent.class));
    }

    @Test
    void perfectScoreEmitsCorrectEventWithFixedIntensity() {
        stubSuccessfulWrite();

        submissionService.submit(new CreateSubmissionRequest(List.of(
                new AnswerSubmissionRequest(FIRST_ID, 12),
                new AnswerSubmissionRequest(SECOND_ID, 27),
                new AnswerSubmissionRequest(THIRD_ID, 12))));

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertEquals("correct", outboxCaptor.getValue().getPayload().event());
        assertEquals(3, outboxCaptor.getValue().getPayload().intensity());
    }

    @Test
    void rejectsDuplicateQuestionIdsBeforeWriting() {
        givenQuestions();
        CreateSubmissionRequest request = new CreateSubmissionRequest(List.of(
                new AnswerSubmissionRequest(FIRST_ID, 12),
                new AnswerSubmissionRequest(FIRST_ID, 12),
                new AnswerSubmissionRequest(THIRD_ID, 12)));

        assertThrows(InvalidSubmissionException.class, () -> submissionService.submit(request));
        verify(submissionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rejectsMissingAnswersBeforeWriting() {
        givenQuestions();
        CreateSubmissionRequest request = new CreateSubmissionRequest(List.of(
                new AnswerSubmissionRequest(FIRST_ID, 12),
                new AnswerSubmissionRequest(SECOND_ID, 27)));

        assertThrows(InvalidSubmissionException.class, () -> submissionService.submit(request));
        verify(submissionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownQuestionIdsBeforeWriting() {
        givenQuestions();
        CreateSubmissionRequest request = new CreateSubmissionRequest(List.of(
                new AnswerSubmissionRequest(FIRST_ID, 12),
                new AnswerSubmissionRequest(SECOND_ID, 27),
                new AnswerSubmissionRequest(UUID.randomUUID(), 12)));

        assertThrows(InvalidSubmissionException.class, () -> submissionService.submit(request));
        verify(submissionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void rejectsMalformedAnswersBeforeWriting() {
        givenQuestions();
        CreateSubmissionRequest request = new CreateSubmissionRequest(List.of(
                new AnswerSubmissionRequest(FIRST_ID, 12),
                new AnswerSubmissionRequest(SECOND_ID, null),
                new AnswerSubmissionRequest(THIRD_ID, 12)));

        assertThrows(InvalidSubmissionException.class, () -> submissionService.submit(request));
        verify(submissionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void capabilityLookupReturnsPendingWithoutLeakingTheObjectKey() {
        UUID submissionId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Submission stored = new Submission(
                submissionId,
                List.of(),
                2,
                3,
                new byte[32],
                Instant.parse("2026-09-16T00:00:00Z"));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(stored));
        when(capabilityGenerator.matches(any(byte[].class), eq("plain-capability"))).thenReturn(true);

        SubmissionDetailResponse detail = submissionService.getByCapability(submissionId, "plain-capability");

        assertEquals(submissionId, detail.id());
        assertEquals(2, detail.score());
        assertEquals(SubmissionDetailResponse.PENDING, detail.accessoryStatus());
    }

    @Test
    void capabilityLookupReportsReadyOnceAnAccessoryKeyExists() {
        UUID submissionId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Submission stored = new Submission(
                submissionId,
                List.of(),
                2,
                3,
                new byte[32],
                Instant.parse("2026-09-16T00:00:00Z"));
        stored.setAccessoryKey(AccessoryKeys.forSubmission(submissionId));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(stored));
        when(capabilityGenerator.matches(any(byte[].class), eq("plain-capability"))).thenReturn(true);

        SubmissionDetailResponse detail = submissionService.getByCapability(submissionId, "plain-capability");
        assertEquals(SubmissionDetailResponse.READY, detail.accessoryStatus());
    }

    @Test
    void missingOrWrongCapabilityCannotReadASubmission() {
        UUID submissionId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThrows(
                SubmissionNotFoundException.class, () -> submissionService.getByCapability(submissionId, null));
        assertThrows(
                SubmissionNotFoundException.class, () -> submissionService.getByCapability(submissionId, " "));
        verify(submissionRepository, never()).findById(any());

        Submission stored = new Submission(
                submissionId,
                List.of(),
                2,
                3,
                new byte[32],
                Instant.parse("2026-09-16T00:00:00Z"));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(stored));
        when(capabilityGenerator.matches(any(byte[].class), eq("wrong"))).thenReturn(false);
        assertThrows(
                SubmissionNotFoundException.class, () -> submissionService.getByCapability(submissionId, "wrong"));

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.empty());
        assertThrows(
                SubmissionNotFoundException.class,
                () -> submissionService.getByCapability(submissionId, "plain-capability"));
    }

    private void stubSuccessfulWrite() {
        givenQuestions();
        when(capabilityGenerator.generate())
                .thenReturn(new GeneratedCapability("plain-capability", new byte[32]));
        when(submissionRepository.save(any(Submission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deviceAssignmentService.requireAssignedDevice())
                .thenReturn(new Device(UUID.randomUUID(), "esp32-dev-001", Instant.parse("2026-09-16T00:00:00Z")));
    }

    private List<Question> questions() {
        Instant createdAt = Instant.parse("2026-09-12T00:00:00Z");
        return List.of(
                new Question(FIRST_ID, "What is 7 + 5?", List.of(), 12, 1, 1, createdAt),
                new Question(SECOND_ID, "What is 9 × 3?", List.of(), 27, 1, 2, createdAt),
                new Question(THIRD_ID, "What is 20 - 8?", List.of(), 12, 1, 3, createdAt));
    }
}
