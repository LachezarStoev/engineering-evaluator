package com.pronet.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.pronet.evaluator.domain.*;
import com.pronet.evaluator.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class LevelFitServiceTest {
    @Test
    void keepsMissingEvidenceInTheDenominatorInsteadOfReportingAPerfectScore() {
        var levels = mock(LevelRepository.class);
        var evidence = mock(EvidenceRepository.class);
        var identities = mock(ExternalIdentityRepository.class);
        var connectors = mock(ConnectorConfigRepository.class);
        var employees = mock(EmployeeRepository.class);
        var catalog = mock(CriteriaCatalogService.class);
        var service =
                new LevelFitService(levels, evidence, identities, connectors, employees, catalog);

        var employee = new Employee();
        employee.setCanonicalEmail("developer@example.com");
        var level = new EngineeringLevel();
        level.setCode("MID_I");
        level.setName("Mid I");
        level.setOrdinalValue(3);

        var mandatory = criterion("jira", "story_points", true, "250");
        var optional = criterion("gitlab", "review_comments", false, "1");
        var optionalEvidence = new Evidence();
        optionalEvidence.setToolKey("gitlab");
        optionalEvidence.setMetricKey("review_comments");
        optionalEvidence.setNumericValue(BigDecimal.ONE);

        var from = Instant.parse("2026-07-01T00:00:00Z");
        var to = Instant.parse("2026-10-01T00:00:00Z");
        when(employees.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(levels.findByVersionAndStatusOrderByOrdinalValueAsc(2, ConfigStatus.PUBLISHED))
                .thenReturn(List.of(level));
        when(catalog.publishedFor(employee, "MID_I", 2)).thenReturn(List.of(mandatory, optional));
        when(evidence.findByEmployeeIdAndToolKeyAndOccurredAtBetween(
                        employee.getId(), "jira", from, to))
                .thenReturn(List.of());
        when(identities.findByEmployeeIdAndToolKey(employee.getId(), "jira"))
                .thenReturn(Optional.empty());
        when(evidence.findByEmployeeIdAndToolKeyAndOccurredAtBetween(
                        employee.getId(), "gitlab", from, to))
                .thenReturn(List.of(optionalEvidence));
        when(evidence.findByEmployeeIdAndMetricKeyAndOccurredAtBetween(
                        employee.getId(), "review_comments", from, to))
                .thenReturn(List.of(optionalEvidence));

        var fit = service.evaluate(employee.getId(), from, to, "UTC", 2).getFirst();

        assertThat(fit.score()).isEqualTo(50);
        assertThat(fit.automaticCriteria()).isEqualTo(2);
        assertThat(fit.incompleteCriteria()).isEqualTo(1);
        assertThat(fit.passedAutomaticCriteria()).isEqualTo(1);
        assertThat(fit.recommended()).isFalse();
        verify(evidence)
                .findByEmployeeIdAndMetricKeyAndOccurredAtBetween(
                        employee.getId(), "review_comments", from, to);
    }

    private static Criterion criterion(
            String tool, String metric, boolean mandatory, String threshold) {
        var criterion = new Criterion();
        criterion.setSourceTool(tool);
        criterion.setMetricKey(metric);
        criterion.setMandatory(mandatory);
        criterion.setEvaluationType(EvaluationType.AUTOMATIC);
        criterion.setAggregation(Aggregation.SUM);
        criterion.setOperator(">=");
        criterion.setThresholdValue(new BigDecimal(threshold));
        criterion.setPeriodType("QUARTER");
        return criterion;
    }
}
