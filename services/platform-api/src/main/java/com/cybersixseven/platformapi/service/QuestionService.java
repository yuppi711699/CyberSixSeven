package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.dto.QuestionResponse;
import com.cybersixseven.platformapi.dto.StaffQuestionResponse;
import com.cybersixseven.platformapi.dto.UpsertQuestionRequest;
import com.cybersixseven.platformapi.entity.Question;
import com.cybersixseven.platformapi.repository.QuestionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;

    public QuestionService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listQuestions() {
        return questionRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(question -> new QuestionResponse(
                        question.getId(),
                        question.getPrompt(),
                        question.getOptions(),
                        question.getDisplayOrder()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StaffQuestionResponse> listForStaff() {
        return questionRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(this::toStaff)
                .toList();
    }

    @Transactional(readOnly = true)
    public StaffQuestionResponse getForStaff(UUID id) {
        return toStaff(require(id));
    }

    @Transactional
    public StaffQuestionResponse create(UpsertQuestionRequest request) {
        ValidatedQuestion input = validate(request);
        Question question = new Question(
                UUID.randomUUID(),
                input.prompt(),
                input.options(),
                input.correctAnswer(),
                input.maxPoints(),
                input.displayOrder(),
                Instant.now());
        return toStaff(save(question));
    }

    @Transactional
    public StaffQuestionResponse update(UUID id, UpsertQuestionRequest request) {
        ValidatedQuestion input = validate(request);
        Question question = require(id);
        question.update(
                input.prompt(), input.options(), input.correctAnswer(), input.maxPoints(), input.displayOrder());
        return toStaff(save(question));
    }

    @Transactional
    public void delete(UUID id) {
        Question question = require(id);
        questionRepository.delete(question);
    }

    private Question require(UUID id) {
        if (id == null) {
            throw new QuestionNotFoundException();
        }
        return questionRepository.findById(id).orElseThrow(QuestionNotFoundException::new);
    }

    private Question save(Question question) {
        try {
            return questionRepository.saveAndFlush(question);
        } catch (DataIntegrityViolationException exception) {
            throw new DisplayOrderConflictException();
        }
    }

    private ValidatedQuestion validate(UpsertQuestionRequest request) {
        if (request == null
                || request.prompt() == null
                || request.prompt().isBlank()
                || request.options() == null
                || request.correctAnswer() == null
                || request.maxPoints() == null
                || request.maxPoints() <= 0
                || request.displayOrder() == null
                || request.displayOrder() <= 0) {
            throw new InvalidQuestionException(
                    "prompt, options, correctAnswer, maxPoints > 0, and displayOrder > 0 are required");
        }
        return new ValidatedQuestion(
                request.prompt().trim(),
                List.copyOf(request.options()),
                request.correctAnswer(),
                request.maxPoints(),
                request.displayOrder());
    }

    private StaffQuestionResponse toStaff(Question question) {
        return new StaffQuestionResponse(
                question.getId(),
                question.getPrompt(),
                question.getOptions(),
                question.getCorrectAnswer(),
                question.getMaxPoints(),
                question.getDisplayOrder());
    }

    private record ValidatedQuestion(
            String prompt, List<Integer> options, int correctAnswer, int maxPoints, int displayOrder) {}
}
