package com.pronet.evaluator.repository;

import com.pronet.evaluator.domain.Evaluation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {
    List<Evaluation> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    Optional<Evaluation> findByEmployeeIdAndPeriodAndLevelCodeAndRuleVersion(
            UUID employeeId, String period, String levelCode, int version);
}
