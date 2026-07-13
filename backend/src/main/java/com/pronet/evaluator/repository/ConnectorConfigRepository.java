package com.pronet.evaluator.repository;

import com.pronet.evaluator.domain.ConnectorConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectorConfigRepository extends JpaRepository<ConnectorConfig, UUID> {
    Optional<ConnectorConfig> findByToolKey(String toolKey);
}
