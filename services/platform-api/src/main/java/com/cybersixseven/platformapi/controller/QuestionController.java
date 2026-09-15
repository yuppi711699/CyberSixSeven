package com.cybersixseven.platformapi.controller;

import com.cybersixseven.platformapi.dto.QuestionResponse;
import com.cybersixseven.platformapi.service.QuestionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public List<QuestionResponse> listQuestions() {
        return questionService.listQuestions();
    }
}
