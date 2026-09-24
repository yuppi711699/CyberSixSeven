package com.cybersixseven.platformapi.repository;

import com.cybersixseven.platformapi.entity.Submission;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    Optional<Submission> findByIdAndStudentId(UUID id, UUID studentId);

    Page<Submission> findByStudentId(UUID studentId, Pageable pageable);
}
