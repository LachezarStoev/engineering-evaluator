package com.pronet.evaluator.repository;

import com.pronet.evaluator.domain.Evidence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {
    List<Evidence> findByEmployeeIdAndMetricKeyAndOccurredAtBetween(
            UUID employeeId, String metricKey, Instant from, Instant to);

    List<Evidence> findByEmployeeIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID employeeId, Instant from, Instant to);

    List<Evidence> findByEmployeeIdOrderByOccurredAtDesc(UUID employeeId);

    Optional<Evidence> findByToolKeyAndExternalIdAndMetricKey(
            String toolKey, String externalId, String metricKey);

    @Transactional
    void deleteByEmployeeIdAndToolKeyAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            UUID employeeId, String toolKey, Instant from, Instant to);
}
