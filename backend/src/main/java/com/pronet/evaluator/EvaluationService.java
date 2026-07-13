package com.pronet.evaluator;

import com.pronet.evaluator.domain.*;
import com.pronet.evaluator.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class EvaluationService {
    private final EmployeeRepository employees;
    private final CriterionRepository criteria;
    private final EvidenceRepository evidence;
    private final EvaluationRepository evaluations;
    private final ConnectorConfigRepository connectors;

    @Transactional
    Evaluation calculate(String email, String period, String levelCode, int ruleVersion) {
        var range = quarter(period);
        return calculate(email, period, levelCode, ruleVersion, range[0], range[1]);
    }

    @Transactional
    Evaluation calculate(
            String email,
            String period,
            String levelCode,
            int ruleVersion,
            Instant from,
            Instant to) {
        return calculate(email, period, levelCode, ruleVersion, from, to, "UTC");
    }

    @Transactional
    Evaluation calculate(
            String email,
            String period,
            String levelCode,
            int ruleVersion,
            Instant from,
            Instant to,
            String timezone) {
        if (!from.isBefore(to)) throw new IllegalArgumentException("from must be before to");
        var employee =
                employees
                        .findByCanonicalEmailIgnoreCase(normalize(email))
                        .orElseThrow(
                                () -> new NoSuchElementException("Employee not found: " + email));
        var existing =
                evaluations.findByEmployeeIdAndPeriodAndLevelCodeAndRuleVersion(
                        employee.getId(), period, levelCode, ruleVersion);
        if (existing.isPresent() && existing.get().isFinalized())
            throw new IllegalStateException("Finalized evaluations are immutable");
        existing.ifPresent(evaluations::delete);
        var range = new Instant[] {from, to};
        var evaluation = new Evaluation();
        evaluation.setEmployeeId(employee.getId());
        evaluation.setPeriod(period);
        evaluation.setLevelCode(levelCode);
        evaluation.setRuleVersion(ruleVersion);
        evaluation.setPeriodFrom(from);
        evaluation.setPeriodTo(to);
        evaluation.setPeriodTimezone(timezone);
        evaluation.setStatus(EvaluationStatus.READY_FOR_REVIEW);
        for (var criterion :
                criteria.findByLevelCodeAndStatusOrderByCode(levelCode, ConfigStatus.PUBLISHED)) {
            var items =
                    evidence.findByEmployeeIdAndMetricKeyAndOccurredAtBetween(
                            employee.getId(), criterion.getMetricKey(), range[0], range[1]);
            var included = items.stream().filter(Evidence::isIncluded).toList();
            var result = new CriterionResult();
            result.setEvaluation(evaluation);
            result.setCriterionId(criterion.getId());
            result.setThresholdValue(criterion.getThresholdValue());
            result.setFormula(formula(criterion));
            var denominatorItems =
                    criterion.getAggregation() == Aggregation.RATIO
                                    && criterion.getDenominatorMetricKey() != null
                            ? evidence
                                    .findByEmployeeIdAndMetricKeyAndOccurredAtBetween(
                                            employee.getId(),
                                            criterion.getDenominatorMetricKey(),
                                            range[0],
                                            range[1])
                                    .stream()
                                    .filter(Evidence::isIncluded)
                                    .toList()
                            : List.<Evidence>of();
            if (items.isEmpty() && denominatorItems.isEmpty()) {
                var source = connectors.findByToolKey(criterion.getSourceTool());
                boolean unavailable =
                        source.isEmpty() || !"HEALTHY".equals(source.get().getHealthStatus());
                result.setCoverage(unavailable ? "SOURCE_UNAVAILABLE" : "NO_DATA");
                result.setResultStatus(
                        unavailable ? ResultStatus.SOURCE_UNAVAILABLE : ResultStatus.NO_DATA);
            } else {
                var value = aggregate(criterion, included, employee.getId(), range);
                result.setMeasuredValue(value);
                result.setCoverage("COMPLETE");
                if (criterion.getEvaluationType() == EvaluationType.MANAGER_REVIEWED
                        || criterion.getEvaluationType() == EvaluationType.EVIDENCE_ONLY)
                    result.setResultStatus(ResultStatus.NEEDS_REVIEW);
                else
                    result.setResultStatus(
                            compare(value, criterion.getOperator(), criterion.getThresholdValue())
                                    ? ResultStatus.PASS
                                    : ResultStatus.FAIL);
            }
            evaluation.getResults().add(result);
        }
        return evaluations.save(evaluation);
    }

    @Transactional
    Evaluation finalizeEvaluation(UUID id) {
        var e = evaluations.findById(id).orElseThrow();
        e.setFinalized(true);
        e.setStatus(EvaluationStatus.FINALIZED);
        e.setFinalizedAt(Instant.now());
        return e;
    }

    private static boolean compare(BigDecimal v, String op, BigDecimal t) {
        if (t == null) return false;
        int c = v.compareTo(t);
        return switch (op) {
            case ">" -> c > 0;
            case ">=" -> c >= 0;
            case "<" -> c < 0;
            case "<=" -> c <= 0;
            case "=" -> c == 0;
            default -> throw new IllegalArgumentException("Unsupported operator: " + op);
        };
    }

    private BigDecimal aggregate(
            Criterion c, List<Evidence> rows, UUID employeeId, Instant[] range) {
        return switch (c.getAggregation()) {
            case SUM ->
                    rows.stream()
                            .map(
                                    e ->
                                            Optional.ofNullable(e.getNumericValue())
                                                    .orElse(BigDecimal.ZERO))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            case COUNT -> BigDecimal.valueOf(rows.size());
            case RATIO -> {
                if (c.getDenominatorMetricKey() == null)
                    throw new IllegalArgumentException("RATIO requires denominatorMetricKey");
                var denominator =
                        evidence
                                .findByEmployeeIdAndMetricKeyAndOccurredAtBetween(
                                        employeeId, c.getDenominatorMetricKey(), range[0], range[1])
                                .stream()
                                .filter(Evidence::isIncluded)
                                .count();
                yield denominator == 0
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(rows.size() * 100.0 / denominator)
                                .setScale(2, java.math.RoundingMode.HALF_UP);
            }
        };
    }

    private static String formula(Criterion c) {
        return switch (c.getAggregation()) {
                    case SUM -> "SUM(" + c.getSourceTool() + "." + c.getMetricKey() + ")";
                    case COUNT -> "COUNT(" + c.getSourceTool() + "." + c.getMetricKey() + ")";
                    case RATIO ->
                            "COUNT("
                                    + c.getMetricKey()
                                    + ") / COUNT("
                                    + c.getDenominatorMetricKey()
                                    + ") * 100";
                }
                + " "
                + c.getOperator()
                + " "
                + c.getThresholdValue();
    }

    static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static Instant[] quarter(String value) {
        var p = value.toUpperCase(Locale.ROOT).split("-Q");
        int year = Integer.parseInt(p[0]), q = Integer.parseInt(p[1]);
        var start = LocalDate.of(year, (q - 1) * 3 + 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Instant[] {
            start,
            LocalDate.of(year, q * 3, 1).plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        };
    }
}
