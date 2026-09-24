package com.cybersixseven.platformapi.controller;

import com.cybersixseven.platformapi.dto.StaffQuestionResponse;
import com.cybersixseven.platformapi.dto.UpsertQuestionRequest;
import com.cybersixseven.platformapi.service.QuestionService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/questions")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class AdminQuestionController {

    private final QuestionService questionService;

    public AdminQuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public List<StaffQuestionResponse> list() {
        return questionService.listForStaff();
    }

    @GetMapping("/{id}")
    public StaffQuestionResponse get(@PathVariable UUID id) {
        return questionService.getForStaff(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffQuestionResponse create(@RequestBody UpsertQuestionRequest request) {
        return questionService.create(request);
    }

    @PutMapping("/{id}")
    public StaffQuestionResponse update(@PathVariable UUID id, @RequestBody UpsertQuestionRequest request) {
        return questionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        questionService.delete(id);
    }
}
