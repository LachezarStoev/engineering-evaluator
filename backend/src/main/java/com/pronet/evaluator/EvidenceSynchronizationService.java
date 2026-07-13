package com.pronet.evaluator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pronet.evaluator.domain.*;
import com.pronet.evaluator.repository.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class EvidenceSynchronizationService {
    private final List<EngineeringConnector> connectors;
    private final ExternalIdentityRepository identities;
    private final EvidenceRepository evidence;
    private final ConnectorConfigRepository configs;
    private final ObjectMapper mapper;

    @Scheduled(cron = "${app.sync.cron:0 15 2 * * *}")
    @Transactional
    void scheduled() {
        syncAll(Instant.now().minus(Duration.ofDays(100)), Instant.now());
    }

    @Transactional
    int syncAll(Instant from, Instant to) {
        int count = 0;
        for (var identity : identities.findAll())
            if (identity.isVerified()) count += sync(identity, from, to);
        return count;
    }

    @Transactional
    int syncEmployee(UUID employeeId, Instant from, Instant to) {
        return identities.findByEmployeeId(employeeId).stream()
                .filter(ExternalIdentity::isVerified)
                .mapToInt(identity -> sync(identity, from, to))
                .sum();
    }

    @Transactional
    int sync(ExternalIdentity identity, Instant from, Instant to) {
        var connector =
                connectors.stream()
                        .filter(c -> c.key().equals(identity.getToolKey()))
                        .findFirst()
                        .orElseThrow();
        var health = connector.testConnection();
        var cfg =
                configs.findByToolKey(connector.key())
                        .orElseGet(
                                () -> {
                                    var c = new ConnectorConfig();
                                    c.setToolKey(connector.key());
                                    c.setDisplayName(connector.key());
                                    return c;
                                });
        cfg.setHealthStatus(health.healthy() ? "HEALTHY" : "ERROR");
        if (!health.healthy()) {
            configs.save(cfg);
            return 0;
        }
        var inputs = connector.syncEvidence(identity.getExternalUserId(), from, to);
        evidence.deleteByEmployeeIdAndToolKeyAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                identity.getEmployeeId(), connector.key(), from, to);
        int saved = 0;
        for (var input : inputs) {
            var row =
                    evidence.findByToolKeyAndExternalIdAndMetricKey(
                                    connector.key(), input.externalId(), input.metricKey())
                            .orElseGet(Evidence::new);
            row.setEmployeeId(identity.getEmployeeId());
            row.setToolKey(connector.key());
            row.setMetricKey(input.metricKey());
            row.setExternalId(input.externalId());
            row.setOccurredAt(input.occurredAt());
            row.setNumericValue(input.value());
            row.setTitle(input.title());
            row.setUrl(input.url());
            try {
                row.setAttributesJson(mapper.writeValueAsString(input.attributes()));
            } catch (Exception e) {
                row.setAttributesJson("{}");
            }
            evidence.save(row);
            saved++;
        }
        cfg.setEnabled(true);
        cfg.setLastSyncAt(Instant.now());
        cfg.setHealthStatus("HEALTHY");
        configs.save(cfg);
        return saved;
    }
}
