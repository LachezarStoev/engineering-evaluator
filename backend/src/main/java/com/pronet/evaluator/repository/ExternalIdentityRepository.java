package com.pronet.evaluator.repository;

import com.pronet.evaluator.domain.ExternalIdentity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {
    List<ExternalIdentity> findByEmployeeId(UUID employeeId);

    Optional<ExternalIdentity> findByEmployeeIdAndToolKey(UUID employeeId, String toolKey);
}
