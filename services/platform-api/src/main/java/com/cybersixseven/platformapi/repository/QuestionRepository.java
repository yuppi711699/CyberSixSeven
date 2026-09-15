package com.cybersixseven.platformapi.repository;

import com.cybersixseven.platformapi.entity.Question;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findAllByOrderByDisplayOrderAsc();
}
