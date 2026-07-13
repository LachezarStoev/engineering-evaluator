package com.pronet.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.pronet.evaluator.domain.Employee;
import com.pronet.evaluator.domain.Evaluation;
import com.pronet.evaluator.repository.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class DataReadinessServiceTest {
    @Test
    void reportsAuthenticationFailureWithoutClaimingThatTheEmployeeWasNotFound() {
        var employees = mock(EmployeeRepository.class);
        var evaluations = mock(EvaluationRepository.class);
        var identities = mock(ExternalIdentityRepository.class);
        var evidence = mock(EvidenceRepository.class);
        var discovery = mock(IdentityDiscoveryService.class);
        var service =
                new DataReadinessService(employees, evaluations, identities, evidence, discovery);

        var employee = new Employee();
        employee.setCanonicalEmail("developer@example.com");
        var evaluation = new Evaluation();
        evaluation.setEmployeeId(employee.getId());
        evaluation.setPeriodFrom(Instant.parse("2026-07-01T00:00:00Z"));
        evaluation.setPeriodTo(Instant.parse("2026-07-14T00:00:00Z"));
        var health =
                new ConnectorHealth(
                        false,
                        "The configured credentials were rejected (HTTP 401)",
                        "AUTHENTICATION_FAILED");

        when(evaluations.findById(evaluation.getId())).thenReturn(Optional.of(evaluation));
        when(employees.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(discovery.discover(employee.getCanonicalEmail()))
                .thenReturn(
                        List.of(
                                new IdentityDiscoveryService.Discovery(
                                        "jira", health, List.of(), false)));

        var jira =
                service.forEvaluation(evaluation.getId()).stream()
                        .filter(item -> item.tool().equals("jira"))
                        .findFirst()
                        .orElseThrow();

        assertThat(jira.connectionStatus()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(jira.identityStatus()).isEqualTo("IDENTITY_NOT_CHECKED");
        assertThat(jira.dataStatus()).isEqualTo("NOT_SYNCED");
        assertThat(jira.message()).contains("HTTP 401");
    }
}
