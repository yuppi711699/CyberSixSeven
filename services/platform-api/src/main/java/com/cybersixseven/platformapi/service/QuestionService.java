package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.dto.QuestionResponse;
import com.cybersixseven.platformapi.repository.QuestionRepository;
import java.util.List;
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
}
