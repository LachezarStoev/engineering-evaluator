package com.pronet.evaluator.repository;

import com.pronet.evaluator.domain.CriterionResult;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CriterionResultRepository extends JpaRepository<CriterionResult, UUID> {}
