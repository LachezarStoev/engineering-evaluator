package com.pronet.evaluator.repository;

import com.pronet.evaluator.domain.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineeringTrackRepository extends JpaRepository<EngineeringTrack, UUID> {
    List<EngineeringTrack> findAllByOrderByOrdinalValueAscVersionDesc();

    Optional<EngineeringTrack> findFirstByCodeIgnoreCaseAndStatusOrderByVersionDesc(
            String code, ConfigStatus status);
}
