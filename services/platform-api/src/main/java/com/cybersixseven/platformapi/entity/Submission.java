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
@Table(name = "submissions")
public class Submission {

    @Id
    private UUID id;

    @Column(name = "student_id")
    private UUID studentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<ScoredAnswerSnapshot> answers;

    @Column(nullable = false)
    private int score;

    @Column(name = "max_score", nullable = false)
    private int maxScore;

    @Column(name = "submission_secret_hash", nullable = false)
    private byte[] submissionSecretHash;

    @Column(name = "accessory_key")
    private String accessoryKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Submission() {}

    public Submission(
            UUID id,
            List<ScoredAnswerSnapshot> answers,
            int score,
            int maxScore,
            byte[] submissionSecretHash,
            Instant createdAt) {
        this.id = id;
        this.studentId = null;
        this.answers = List.copyOf(answers);
        this.score = score;
        this.maxScore = maxScore;
        this.submissionSecretHash = submissionSecretHash.clone();
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public List<ScoredAnswerSnapshot> getAnswers() {
        return List.copyOf(answers);
    }

    public int getScore() {
        return score;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public byte[] getSubmissionSecretHash() {
        return submissionSecretHash.clone();
    }

    public String getAccessoryKey() {
        return accessoryKey;
    }

    public void setAccessoryKey(String accessoryKey) {
        this.accessoryKey = accessoryKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
