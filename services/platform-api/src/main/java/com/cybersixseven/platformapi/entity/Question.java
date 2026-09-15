package com.cybersixseven.platformapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String prompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Integer> options;

    @Column(name = "correct_answer", nullable = false)
    private int correctAnswer;

    @Column(name = "max_points", nullable = false)
    private int maxPoints;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Question() {}

    public Question(
            UUID id,
            String prompt,
            List<Integer> options,
            int correctAnswer,
            int maxPoints,
            int displayOrder,
            Instant createdAt) {
        this.id = id;
        this.prompt = prompt;
        this.options = List.copyOf(options);
        this.correctAnswer = correctAnswer;
        this.maxPoints = maxPoints;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getPrompt() {
        return prompt;
    }

    public List<Integer> getOptions() {
        return List.copyOf(options);
    }

    public int getCorrectAnswer() {
        return correctAnswer;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
