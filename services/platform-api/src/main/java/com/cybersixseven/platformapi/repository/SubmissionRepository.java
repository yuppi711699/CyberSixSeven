package com.cybersixseven.platformapi.repository;

import com.cybersixseven.platformapi.entity.Submission;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {}
