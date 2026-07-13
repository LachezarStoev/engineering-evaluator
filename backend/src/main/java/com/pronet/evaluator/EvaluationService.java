package com.pronet.evaluator;

import com.pronet.evaluator.domain.*;
import com.pronet.evaluator.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class EvaluationService {
    private final EmployeeRepository employees;
    private final EvidenceRepository evidence;
    private final EvaluationRepository evaluations;
    private final ConnectorConfigRepository connectors;
    private final ExternalIdentityRepository identities;
    private final CriteriaCatalogService catalog;

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
        return calculate(
                email, period, levelCode, ruleVersion, from, to, "UTC", EvaluationMode.ASSESSMENT);
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
        return calculate(
                email,
                period,
                levelCode,
                ruleVersion,
                from,
                to,
                timezone,
                EvaluationMode.ASSESSMENT);
    }

    @Transactional
    Evaluation calculate(
            String email,
            String period,
            String levelCode,
            int ruleVersion,
            Instant from,
            Instant to,
            String timezone,
            EvaluationMode mode) {
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
        existing.ifPresent(
                previous -> {
                    evaluations.delete(previous);
                    evaluations.flush();
                });
        var range = new Instant[] {from, to};
        var evaluation = new Evaluation();
        evaluation.setEmployeeId(employee.getId());
        evaluation.setPeriod(period);
        evaluation.setLevelCode(levelCode);
        evaluation.setRuleVersion(ruleVersion);
        evaluation.setPeriodFrom(from);
        evaluation.setPeriodTo(to);
        evaluation.setPeriodTimezone(timezone);
        evaluation.setEvaluationMode(mode == null ? EvaluationMode.SNAPSHOT : mode);
        evaluation.setStatus(EvaluationStatus.READY_FOR_REVIEW);
        for (var criterion : catalog.publishedFor(employee, levelCode, ruleVersion)) {
            var items =
                    evidence.findByEmployeeIdAndMetricKeyAndOccurredAtBetween(
                            employee.getId(), criterion.getMetricKey(), range[0], range[1]);
            var included = items.stream().filter(Evidence::isIncluded).toList();
            var result = new CriterionResult();
            result.setEvaluation(evaluation);
            result.setCriterionId(criterion.getId());
            result.setCriterionName(criterion.getName());
            result.setThresholdValue(criterion.getThresholdValue());
            result.setThresholdMaxValue(criterion.getThresholdMaxValue());
            result.setPeriodTargetValue(
                    proportionalTarget(criterion, from, to, ZoneId.of(timezone)));
            result.setPeriodTargetMaxValue(
                    proportionalTarget(
                            criterion,
                            criterion.getThresholdMaxValue(),
                            from,
                            to,
                            ZoneId.of(timezone)));
            result.setFormula(formula(criterion));
            result.setCadence(criterion.getPeriodType());
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
            boolean humanReviewed =
                    criterion.getEvaluationType() == EvaluationType.MANAGER_REVIEWED
                            || criterion.getEvaluationType() == EvaluationType.EVIDENCE_ONLY;
            var identity =
                    identities.findByEmployeeIdAndToolKey(
                            employee.getId(), criterion.getSourceTool());
            var source = connectors.findByToolKey(criterion.getSourceTool());
            var toolItems =
                    evidence.findByEmployeeIdAndToolKeyAndOccurredAtBetween(
                            employee.getId(), criterion.getSourceTool(), range[0], range[1]);
            boolean sourceUnavailable =
                    source.isEmpty() || !"HEALTHY".equals(source.get().getHealthStatus());
            if (humanReviewed) {
                result.setMeasuredValue(BigDecimal.valueOf(included.size()));
                result.setCoverage(included.isEmpty() ? "HUMAN_REVIEW" : "EVIDENCE_AVAILABLE");
                result.setResultStatus(ResultStatus.NEEDS_REVIEW);
            } else if (toolItems.isEmpty()
                    && (identity.isEmpty() || !identity.get().isVerified())) {
                result.setCoverage("IDENTITY_UNRESOLVED");
                result.setResultStatus(ResultStatus.IDENTITY_UNRESOLVED);
            } else if (toolItems.isEmpty() && sourceUnavailable) {
                result.setCoverage("SOURCE_UNAVAILABLE");
                result.setResultStatus(ResultStatus.SOURCE_UNAVAILABLE);
            } else if (criterion.getAggregation() == Aggregation.RATIO
                    && denominatorItems.isEmpty()) {
                result.setCoverage("NO_DATA");
                result.setResultStatus(ResultStatus.NO_DATA);
            } else {
                var value =
                        aggregateForPeriod(
                                criterion,
                                included,
                                employee.getId(),
                                range,
                                timezone,
                                evaluation.getEvaluationMode());
                result.setMeasuredValue(value);
                result.setCoverage(toolItems.isEmpty() ? "NO_ACTIVITY" : "COMPLETE");
                if (!isComparable(criterion, from, to, timezone, evaluation.getEvaluationMode())) {
                    result.setResultStatus(ResultStatus.NOT_COMPARABLE);
                } else
                    result.setResultStatus(
                            compare(
                                            value,
                                            criterion.getOperator(),
                                            criterion.getThresholdValue(),
                                            criterion.getThresholdMaxValue())
                                    ? ResultStatus.PASS
                                    : ResultStatus.FAIL);
            }
            evaluation.getResults().add(result);
        }
        boolean incomplete =
                evaluation.getResults().stream()
                        .map(CriterionResult::getResultStatus)
                        .anyMatch(
                                status ->
                                        status == ResultStatus.IDENTITY_UNRESOLVED
                                                || status == ResultStatus.SOURCE_UNAVAILABLE
                                                || status == ResultStatus.PARTIAL_DATA);
        evaluation.setStatus(
                incomplete
                        ? EvaluationStatus.INCOMPLETE
                        : evaluation.getEvaluationMode() == EvaluationMode.SNAPSHOT
                                ? EvaluationStatus.SNAPSHOT_READY
                                : EvaluationStatus.READY_FOR_REVIEW);
        return evaluations.save(evaluation);
    }

    @Transactional
    Evaluation finalizeEvaluation(UUID id) {
        var e = evaluations.findById(id).orElseThrow();
        boolean incomplete =
                e.getResults().stream()
                        .map(CriterionResult::getResultStatus)
                        .anyMatch(
                                status ->
                                        status == ResultStatus.IDENTITY_UNRESOLVED
                                                || status == ResultStatus.SOURCE_UNAVAILABLE
                                                || status == ResultStatus.PARTIAL_DATA);
        if (incomplete)
            throw new IllegalStateException(
                    "Evaluation cannot be finalized while required source data is unresolved");
        e.setFinalized(true);
        e.setStatus(EvaluationStatus.FINALIZED);
        e.setFinalizedAt(Instant.now());
        return e;
    }

    static boolean compare(BigDecimal v, String op, BigDecimal t) {
        return compare(v, op, t, null);
    }

    static boolean compare(BigDecimal v, String op, BigDecimal t, BigDecimal max) {
        if (t == null) return false;
        int c = v.compareTo(t);
        return switch (op) {
            case ">" -> c > 0;
            case ">=" -> c >= 0;
            case "<" -> c < 0;
            case "<=" -> c <= 0;
            case "=" -> c == 0;
            case "BETWEEN" -> max != null && c >= 0 && v.compareTo(max) <= 0;
            default -> throw new IllegalArgumentException("Unsupported operator: " + op);
        };
    }

    static String displayStatus(CriterionResult result) {
        if (result.getResultStatus() != ResultStatus.NOT_COMPARABLE
                || result.getMeasuredValue() == null
                || result.getPeriodTargetValue() == null) return result.getResultStatus().name();
        return compare(
                        result.getMeasuredValue(),
                        operator(result.getFormula()),
                        result.getPeriodTargetValue(),
                        result.getPeriodTargetMaxValue())
                ? "ON_PACE"
                : "BEHIND_PACE";
    }

    private static String operator(String formula) {
        if (formula.contains(" BETWEEN ")) return "BETWEEN";
        if (formula.contains(" <= ")) return "<=";
        if (formula.contains(" >= ")) return ">=";
        if (formula.contains(" < ")) return "<";
        if (formula.contains(" > ")) return ">";
        return "=";
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
                                                    RoundingMode.HALF_UP))
                            .orElse(BigDecimal.ZERO);
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

    private BigDecimal aggregateForPeriod(
            Criterion criterion,
            List<Evidence> rows,
            UUID employeeId,
            Instant[] range,
            String timezone,
            EvaluationMode mode) {
        if (mode == EvaluationMode.ASSESSMENT
                && "MONTH".equalsIgnoreCase(criterion.getPeriodType())
                && criterion.getAggregation() == Aggregation.COUNT
                && isWholeMonths(range[0], range[1], ZoneId.of(timezone))) {
            var zone = ZoneId.of(timezone);
            var month = YearMonth.from(range[0].atZone(zone));
            var end = YearMonth.from(range[1].minusNanos(1).atZone(zone));
            BigDecimal minimum = null;
            while (!month.isAfter(end)) {
                var current = month;
                long count =
                        rows.stream()
                                .filter(
                                        item ->
                                                YearMonth.from(item.getOccurredAt().atZone(zone))
                                                        .equals(current))
                                .count();
                var value = BigDecimal.valueOf(count);
                minimum = minimum == null || value.compareTo(minimum) < 0 ? value : minimum;
                month = month.plusMonths(1);
            }
            return minimum == null ? BigDecimal.ZERO : minimum;
        }
        return aggregate(criterion, rows, employeeId, range);
    }

    private static boolean isComparable(
            Criterion criterion, Instant from, Instant to, String timezone, EvaluationMode mode) {
        if (criterion.isCustomPeriodAllowed()
                || criterion.getProrationPolicy() == ProrationPolicy.ALLOWED) return true;
        if (criterion.getProrationPolicy() == ProrationPolicy.FORBIDDEN
                && mode != EvaluationMode.ASSESSMENT) return false;
        if (mode != EvaluationMode.ASSESSMENT) return false;
        var zone = ZoneId.of(timezone);
        return switch (Optional.ofNullable(criterion.getPeriodType()).orElse("CUSTOM")) {
            case "MONTH" -> isWholeMonths(from, to, zone);
            case "QUARTER" -> isCalendarQuarter(from, to, zone);
            case "CUSTOM" -> true;
            default -> false;
        };
    }

    static BigDecimal proportionalTarget(
            Criterion criterion, Instant from, Instant to, ZoneId zone) {
        return proportionalTarget(criterion, criterion.getThresholdValue(), from, to, zone);
    }

    static BigDecimal proportionalTarget(
            Criterion criterion, BigDecimal threshold, Instant from, Instant to, ZoneId zone) {
        if (threshold == null || criterion.getAggregation() == Aggregation.RATIO) return threshold;
        var cadence = Optional.ofNullable(criterion.getPeriodType()).orElse("CUSTOM");
        if ("CUSTOM".equals(cadence)) return threshold;
        var start = from.atZone(zone).toLocalDate();
        var endExclusive = to.atZone(zone).toLocalDate();
        BigDecimal units = BigDecimal.ZERO;
        for (var day = start; day.isBefore(endExclusive); day = day.plusDays(1)) {
            int cadenceDays =
                    switch (cadence) {
                        case "MONTH" -> day.lengthOfMonth();
                        case "QUARTER" -> daysInQuarter(day);
                        default -> 1;
                    };
            units =
                    units.add(
                            BigDecimal.ONE.divide(
                                    BigDecimal.valueOf(cadenceDays), 10, RoundingMode.HALF_UP));
        }
        return threshold.multiply(units).setScale(2, RoundingMode.HALF_UP);
    }

    private static int daysInQuarter(LocalDate day) {
        int firstMonth = ((day.getMonthValue() - 1) / 3) * 3 + 1;
        var firstDay = LocalDate.of(day.getYear(), firstMonth, 1);
        return (int) java.time.temporal.ChronoUnit.DAYS.between(firstDay, firstDay.plusMonths(3));
    }

    private static boolean isWholeMonths(Instant from, Instant to, ZoneId zone) {
        var start = from.atZone(zone);
        var end = to.atZone(zone);
        return start.toLocalTime().equals(LocalTime.MIDNIGHT)
                && start.getDayOfMonth() == 1
                && end.toLocalTime().equals(LocalTime.MIDNIGHT)
                && end.getDayOfMonth() == 1
                && start.isBefore(end);
    }

    private static boolean isCalendarQuarter(Instant from, Instant to, ZoneId zone) {
        var start = from.atZone(zone);
        return isWholeMonths(from, to, zone)
                && Set.of(1, 4, 7, 10).contains(start.getMonthValue())
                && start.plusMonths(3).toInstant().equals(to);
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
                    case AVERAGE -> "AVERAGE(" + c.getSourceTool() + "." + c.getMetricKey() + ")";
                }
                + " "
                + c.getOperator()
                + " "
                + c.getThresholdValue()
                + ("BETWEEN".equals(c.getOperator()) ? " AND " + c.getThresholdMaxValue() : "");
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
