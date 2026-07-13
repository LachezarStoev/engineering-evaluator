package com.pronet.evaluator.repository;

import com.pronet.evaluator.domain.ConfigStatus;
import com.pronet.evaluator.domain.Criterion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CriterionRepository extends JpaRepository<Criterion, UUID> {
    List<Criterion> findByLevelCodeAndStatusOrderByCode(String levelCode, ConfigStatus status);
}
