package com.pronet.evaluator;

import com.pronet.evaluator.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class DataReadinessService {
    private static final List<String> TOOLS = List.of("gitlab", "jira", "confluence");

    private final EmployeeRepository employees;
    private final EvaluationRepository evaluations;
    private final ExternalIdentityRepository identities;
    private final EvidenceRepository evidence;
    private final IdentityDiscoveryService discovery;

    record Readiness(
            String tool,
            String connectionStatus,
            String identityStatus,
            String dataStatus,
            long evidenceCount,
            String message) {}

    List<Readiness> forEvaluation(UUID evaluationId) {
        var evaluation = evaluations.findById(evaluationId).orElseThrow();
        var employee = employees.findById(evaluation.getEmployeeId()).orElseThrow();
        var discoveries = discovery.discover(employee.getCanonicalEmail());
        var byTool =
                discoveries.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        IdentityDiscoveryService.Discovery::tool, item -> item));

        return TOOLS.stream()
                .map(
                        tool -> {
                            var health =
                                    Optional.ofNullable(byTool.get(tool))
                                            .map(IdentityDiscoveryService.Discovery::health)
                                            .orElse(new ConnectorHealth(false, "not configured"));
                            var identity =
                                    identities.findByEmployeeIdAndToolKey(employee.getId(), tool);
                            boolean verified = identity.isPresent() && identity.get().isVerified();
                            var candidates =
                                    Optional.ofNullable(byTool.get(tool))
                                            .map(IdentityDiscoveryService.Discovery::candidates)
                                            .orElse(List.of());
                            String identityStatus =
                                    verified
                                            ? "IDENTITY_VERIFIED"
                                            : !health.healthy()
                                                    ? "IDENTITY_NOT_CHECKED"
                                                    : candidates.isEmpty()
                                                            ? "IDENTITY_NOT_FOUND"
                                                            : "IDENTITY_NEEDS_CONFIRMATION";
                            long count =
                                    verified && evaluation.getPeriodFrom() != null
                                            ? evidence.findByEmployeeIdAndToolKeyAndOccurredAtBetween(
                                                            employee.getId(),
                                                            tool,
                                                            evaluation.getPeriodFrom(),
                                                            evaluation.getPeriodTo())
                                                    .size()
                                            : 0;
                            String dataStatus =
                                    !health.healthy()
                                            ? "NOT_SYNCED"
                                            : !verified
                                                    ? "NOT_SYNCED"
                                                    : count == 0 ? "NO_ACTIVITY" : "SYNCED";
                            return new Readiness(
                                    tool,
                                    health.status(),
                                    identityStatus,
                                    dataStatus,
                                    count,
                                    health.message());
                        })
                .toList();
    }
}
