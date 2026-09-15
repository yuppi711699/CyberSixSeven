package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cybersixseven.platformapi.dto.AnswerSubmissionRequest;
import com.cybersixseven.platformapi.dto.CreateSubmissionRequest;
import com.cybersixseven.platformapi.dto.CreateSubmissionResponse;
import com.cybersixseven.platformapi.entity.Question;
import com.cybersixseven.platformapi.entity.Submission;
import com.cybersixseven.platformapi.repository.QuestionRepository;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import com.cybersixseven.platformapi.service.SubmissionCapabilityGenerator.GeneratedCapability;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private SubmissionCapabilityGenerator capabilityGenerator;

    private SubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionService =
                new SubmissionService(questionRepository, submissionRepository, capabilityGenerator);
        when(questionRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(questions());
    }

    @Test
    void scoresFromStoredQuestionsAndPersistsAnImmutableSnapshot() {
        when(capabilityGenerator.generate())
                .thenReturn(new GeneratedCapability("plain-capability", new byte[32]));
        when(submissionRepository.save(any(Submission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(captor.capture());
        Submission stored = captor.getValue();
        assertEquals(2, stored.getScore());
        assertEquals("What is 7 + 5?", stored.getAnswers().get(0).prompt());
        assertEquals(12, stored.getAnswers().get(0).correctAnswer());
    }

    @Test
    void rejectsDuplicateQuestionIdsBeforeWriting() {
        CreateSubmissionRequest request = new CreateSubmissionRequest(List.of(
                new AnswerSubmissionRequest(FIRST_ID, 12),
                new AnswerSubmissionRequest(FIRST_ID, 12),
                new AnswerSubmissionRequest(THIRD_ID, 12)));

        assertThrows(InvalidSubmissionException.class, () -> submissionService.submit(request));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void rejectsMissingAnswersBeforeWriting() {
        CreateSubmissionRequest request = new CreateSubmissionRequest(List.of(
                new AnswerSubmissionRequest(FIRST_ID, 12),
                new AnswerSubmissionRequest(SECOND_ID, 27)));

        assertThrows(InvalidSubmissionException.class, () -> submissionService.submit(request));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownQuestionIdsBeforeWriting() {
        CreateSubmissionRequest request = new CreateSubmissionRequest(List.of(
                new AnswerSubmissionRequest(FIRST_ID, 12),
                new AnswerSubmissionRequest(SECOND_ID, 27),
                new AnswerSubmissionRequest(UUID.randomUUID(), 12)));

        assertThrows(InvalidSubmissionException.class, () -> submissionService.submit(request));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void rejectsMalformedAnswersBeforeWriting() {
        CreateSubmissionRequest request = new CreateSubmissionRequest(List.of(
                new AnswerSubmissionRequest(FIRST_ID, 12),
                new AnswerSubmissionRequest(SECOND_ID, null),
                new AnswerSubmissionRequest(THIRD_ID, 12)));

        assertThrows(InvalidSubmissionException.class, () -> submissionService.submit(request));
        verify(submissionRepository, never()).save(any());
    }

    private List<Question> questions() {
        Instant createdAt = Instant.parse("2026-09-12T00:00:00Z");
        return List.of(
                new Question(FIRST_ID, "What is 7 + 5?", List.of(), 12, 1, 1, createdAt),
                new Question(SECOND_ID, "What is 9 × 3?", List.of(), 27, 1, 2, createdAt),
                new Question(THIRD_ID, "What is 20 - 8?", List.of(), 12, 1, 3, createdAt));
    }
}
