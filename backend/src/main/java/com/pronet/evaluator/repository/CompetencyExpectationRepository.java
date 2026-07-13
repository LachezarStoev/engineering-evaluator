package com.pronet.evaluator.repository;

import com.pronet.evaluator.domain.CompetencyExpectation;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetencyExpectationRepository
        extends JpaRepository<CompetencyExpectation, UUID> {
    List<CompetencyExpectation> findByVersionOrderByLevelCodeAscCompetencyKeyAsc(int version);
}
