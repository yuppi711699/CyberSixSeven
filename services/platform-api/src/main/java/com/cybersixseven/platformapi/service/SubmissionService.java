package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.dto.AnswerSubmissionRequest;
import com.cybersixseven.platformapi.dto.CreateSubmissionRequest;
import com.cybersixseven.platformapi.dto.CreateSubmissionResponse;
import com.cybersixseven.platformapi.dto.ScoredAnswerResponse;
import com.cybersixseven.platformapi.entity.Question;
import com.cybersixseven.platformapi.entity.ScoredAnswerSnapshot;
import com.cybersixseven.platformapi.entity.Submission;
import com.cybersixseven.platformapi.repository.QuestionRepository;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import com.cybersixseven.platformapi.service.SubmissionCapabilityGenerator.GeneratedCapability;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionService {

    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionCapabilityGenerator capabilityGenerator;

    public SubmissionService(
            QuestionRepository questionRepository,
            SubmissionRepository submissionRepository,
            SubmissionCapabilityGenerator capabilityGenerator) {
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.capabilityGenerator = capabilityGenerator;
    }

    @Transactional
    public CreateSubmissionResponse submit(CreateSubmissionRequest request) {
        List<Question> questions = questionRepository.findAllByOrderByDisplayOrderAsc();
        Map<UUID, Integer> submittedAnswers = validateAndIndex(request, questions);

        List<ScoredAnswerSnapshot> snapshots = questions.stream()
                .map(question -> score(question, submittedAnswers.get(question.getId())))
                .toList();
        int score = snapshots.stream().mapToInt(ScoredAnswerSnapshot::awardedPoints).sum();
        int maxScore = snapshots.stream().mapToInt(ScoredAnswerSnapshot::maxPoints).sum();
        GeneratedCapability capability = capabilityGenerator.generate();

        Submission submission = new Submission(
                UUID.randomUUID(),
                snapshots,
                score,
                maxScore,
                capability.hash(),
                Instant.now());
        submissionRepository.save(submission);

        return new CreateSubmissionResponse(
                submission.getId(),
                capability.plaintext(),
                score,
                maxScore,
                snapshots.stream().map(this::toResponse).toList());
    }

    private Map<UUID, Integer> validateAndIndex(
            CreateSubmissionRequest request, List<Question> questions) {
        if (request == null || request.answers() == null) {
            throw new InvalidSubmissionException("answers is required");
        }
        if (request.answers().size() != questions.size()) {
            throw new InvalidSubmissionException(
                    "exactly " + questions.size() + " answers are required");
        }

        Map<UUID, Integer> submittedAnswers = new HashMap<>();
        for (AnswerSubmissionRequest answer : request.answers()) {
            if (answer == null || answer.questionId() == null || answer.answer() == null) {
                throw new InvalidSubmissionException(
                        "each answer requires questionId and a numeric answer");
            }
            if (submittedAnswers.putIfAbsent(answer.questionId(), answer.answer()) != null) {
                throw new InvalidSubmissionException("duplicate questionId");
            }
        }

        Set<UUID> expectedIds =
                questions.stream().map(Question::getId).collect(Collectors.toUnmodifiableSet());
        if (!expectedIds.containsAll(submittedAnswers.keySet())) {
            throw new InvalidSubmissionException("unknown questionId");
        }
        if (!submittedAnswers.keySet().containsAll(expectedIds)) {
            throw new InvalidSubmissionException("an answer is required for every question");
        }

        return submittedAnswers;
    }

    private ScoredAnswerSnapshot score(Question question, int submittedAnswer) {
        boolean correct = submittedAnswer == question.getCorrectAnswer();
        int awardedPoints = correct ? question.getMaxPoints() : 0;

        return new ScoredAnswerSnapshot(
                question.getId(),
                question.getPrompt(),
                question.getOptions(),
                submittedAnswer,
                question.getCorrectAnswer(),
                correct,
                awardedPoints,
                question.getMaxPoints(),
                question.getDisplayOrder());
    }

    private ScoredAnswerResponse toResponse(ScoredAnswerSnapshot snapshot) {
        return new ScoredAnswerResponse(
                snapshot.questionId(),
                snapshot.prompt(),
                snapshot.options(),
                snapshot.submittedAnswer(),
                snapshot.correctAnswer(),
                snapshot.correct(),
                snapshot.awardedPoints(),
                snapshot.maxPoints(),
                snapshot.displayOrder());
    }
}
