package com.pronet.evaluator;

import com.pronet.evaluator.domain.*;
import com.pronet.evaluator.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class LevelFitService {
    private final LevelRepository levels;
    private final EvidenceRepository evidence;
    private final ExternalIdentityRepository identities;
    private final ConnectorConfigRepository connectors;
    private final EmployeeRepository employees;
    private final CriteriaCatalogService catalog;

    record LevelFit(
            String code,
            String name,
            int ordinal,
            int score,
            int passedAutomaticCriteria,
            int automaticCriteria,
            int humanReviewCriteria,
            int incompleteCriteria,
            boolean recommended) {}

    List<LevelFit> evaluate(
            UUID employeeId, Instant from, Instant to, String timezone, int frameworkVersion) {
        var employee = employees.findById(employeeId).orElseThrow();
        var publishedLevels = new LinkedHashMap<String, EngineeringLevel>();
        levels
                .findByVersionAndStatusOrderByOrdinalValueAsc(
                        frameworkVersion, ConfigStatus.PUBLISHED)
                .stream()
                .forEach(level -> publishedLevels.putIfAbsent(level.getCode(), level));
        var raw =
                publishedLevels.values().stream()
                        .map(
                                level ->
                                        evaluateLevel(
                                                level,
                                                employee,
                                                from,
                                                to,
                                                timezone,
                                                frameworkVersion))
                        .toList();
        var recommendation =
                raw.stream()
                        .filter(
                                fit ->
                                        fit.automaticCriteria() > 0
                                                && fit.incompleteCriteria() == 0
                                                && fit.passedAutomaticCriteria()
                                                        == fit.automaticCriteria())
                        .max(Comparator.comparingInt(LevelFit::ordinal))
                        .map(LevelFit::code)
                        .orElse(null);
        return raw.stream()
                .map(
                        fit ->
                                new LevelFit(
                                        fit.code(),
                                        fit.name(),
                                        fit.ordinal(),
                                        fit.score(),
                                        fit.passedAutomaticCriteria(),
                                        fit.automaticCriteria(),
                                        fit.humanReviewCriteria(),
                                        fit.incompleteCriteria(),
                                        Objects.equals(fit.code(), recommendation)))
                .toList();
    }

    private LevelFit evaluateLevel(
            EngineeringLevel level,
            Employee employee,
            Instant from,
            Instant to,
            String timezone,
            int frameworkVersion) {
        var employeeId = employee.getId();
        var levelCriteria = catalog.publishedFor(employee, level.getCode(), frameworkVersion);
        int automatic = 0;
        int passed = 0;
        int human = 0;
        int incomplete = 0;
        BigDecimal progress = BigDecimal.ZERO;
        for (var criterion : levelCriteria) {
            if (criterion.getEvaluationType() == EvaluationType.MANAGER_REVIEWED
                    || criterion.getEvaluationType() == EvaluationType.EVIDENCE_ONLY) {
                human++;
                continue;
            }
            automatic++;
            var measurement = measure(criterion, employeeId, from, to);
            if (measurement.isEmpty()) {
                incomplete++;
                continue;
            }
            var expected =
                    EvaluationService.proportionalTarget(criterion, from, to, ZoneId.of(timezone));
            var expectedMax =
                    EvaluationService.proportionalTarget(
                            criterion,
                            criterion.getThresholdMaxValue(),
                            from,
                            to,
                            ZoneId.of(timezone));
            var value = measurement.get();
            if (EvaluationService.compare(value, criterion.getOperator(), expected, expectedMax))
                passed++;
            progress =
                    progress.add(
                            progressRatio(value, expected, expectedMax, criterion.getOperator()));
        }
        int score =
                automatic == 0
                        ? 0
                        : progress.multiply(BigDecimal.valueOf(100))
                                .divide(BigDecimal.valueOf(automatic), 0, RoundingMode.HALF_UP)
                                .intValue();
        return new LevelFit(
                level.getCode(),
                level.getName(),
                level.getOrdinalValue(),
                Math.min(score, 100),
                passed,
                automatic,
                human,
                incomplete,
                false);
    }

    private Optional<BigDecimal> measure(
            Criterion criterion, UUID employeeId, Instant from, Instant to) {
        var toolItems =
                evidence.findByEmployeeIdAndToolKeyAndOccurredAtBetween(
                        employeeId, criterion.getSourceTool(), from, to);
        var identity = identities.findByEmployeeIdAndToolKey(employeeId, criterion.getSourceTool());
        var source = connectors.findByToolKey(criterion.getSourceTool());
        if (toolItems.isEmpty() && (identity.isEmpty() || !identity.get().isVerified()))
            return Optional.empty();
        if (toolItems.isEmpty()
                && (source.isEmpty() || !"HEALTHY".equals(source.get().getHealthStatus())))
            return Optional.empty();
        var rows =
                evidence
                        .findByEmployeeIdAndMetricKeyAndOccurredAtBetween(
                                employeeId, criterion.getMetricKey(), from, to)
                        .stream()
                        .filter(Evidence::isIncluded)
                        .toList();
        return switch (criterion.getAggregation()) {
            case SUM ->
                    Optional.of(
                            rows.stream()
                                    .map(
                                            row ->
                                                    Optional.ofNullable(row.getNumericValue())
                                                            .orElse(BigDecimal.ZERO))
                                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            case COUNT -> Optional.of(BigDecimal.valueOf(rows.size()));
            case AVERAGE ->
                    rows.stream()
                            .map(Evidence::getNumericValue)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal::add)
                            .map(
                                    total ->
                                            total.divide(
                                                    BigDecimal.valueOf(rows.size()),
                                                    4,
                                                    RoundingMode.HALF_UP));
            case RATIO -> ratio(criterion, employeeId, from, to, rows.size());
        };
    }

    private Optional<BigDecimal> ratio(
            Criterion criterion, UUID employeeId, Instant from, Instant to, int numerator) {
        if (criterion.getDenominatorMetricKey() == null) return Optional.empty();
        long denominator =
                evidence
                        .findByEmployeeIdAndMetricKeyAndOccurredAtBetween(
                                employeeId, criterion.getDenominatorMetricKey(), from, to)
                        .stream()
                        .filter(Evidence::isIncluded)
                        .count();
        if (denominator == 0) return Optional.empty();
        return Optional.of(
                BigDecimal.valueOf(numerator * 100.0 / denominator)
                        .setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal progressRatio(
            BigDecimal value, BigDecimal target, BigDecimal max, String operator) {
        if (target == null) return BigDecimal.ZERO;
        if ("BETWEEN".equals(operator)) {
            if (value.compareTo(target) < 0) {
                if (target.signum() == 0) return BigDecimal.ONE;
                return value.divide(target, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
            }
            if (max == null || value.compareTo(max) <= 0) return BigDecimal.ONE;
            return max.divide(value, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        }
        boolean lowerIsBetter = "<".equals(operator) || "<=".equals(operator);
        if (lowerIsBetter) {
            if (value.compareTo(target) <= 0) return BigDecimal.ONE;
            if (value.signum() == 0) return BigDecimal.ONE;
            return target.divide(value, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        }
        if (target.signum() == 0) return BigDecimal.ONE;
        return value.divide(target, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
    }
}
