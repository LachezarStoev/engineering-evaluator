package com.pronet.evaluator.repository;

import com.pronet.evaluator.domain.EngineeringLevel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LevelRepository extends JpaRepository<EngineeringLevel, UUID> {
    List<EngineeringLevel> findAllByOrderByOrdinalValueAscVersionDesc();
}
