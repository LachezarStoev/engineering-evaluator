package com.pronet.evaluator.repository;

import com.pronet.evaluator.domain.Dispute;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
    List<Dispute> findByEvaluationIdOrderByCreatedAtDesc(UUID evaluationId);
}
