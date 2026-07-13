package com.pronet.evaluator;

import static org.assertj.core.api.Assertions.*;

import com.pronet.evaluator.domain.Aggregation;
import com.pronet.evaluator.domain.Criterion;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;

class EvaluationServiceTest {
    @Test
    void comparesConfiguredRangesWithoutTurningThemIntoOpaqueScores() {
        assertThat(
                        EvaluationService.compare(
                                new BigDecimal("180"),
                                "BETWEEN",
                                new BigDecimal("180"),
                                new BigDecimal("220")))
                .isTrue();
        assertThat(
                        EvaluationService.compare(
                                new BigDecimal("220"),
                                "BETWEEN",
                                new BigDecimal("180"),
                                new BigDecimal("220")))
                .isTrue();
        assertThat(
                        EvaluationService.compare(
                                new BigDecimal("221"),
                                "BETWEEN",
                                new BigDecimal("180"),
                                new BigDecimal("220")))
                .isFalse();
    }

    @Test
    void normalizesCorporateEmail() {
        assertThat(EvaluationService.normalize("  Person.Name@Example.COM "))
                .isEqualTo("person.name@example.com");
    }

    @Test
    void parsesOnlyValidQuarterThroughApiContract() {
        assertThatThrownBy(() -> new java.text.SimpleDateFormat("yyyy-'Q'q").parse("bad"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void proratesQuarterlySumBySelectedCalendarDays() {
        var criterion = criterion("QUARTER", Aggregation.SUM, "250");
        var zone = ZoneId.of("Europe/Sofia");

        assertThat(
                        EvaluationService.proportionalTarget(
                                criterion,
                                LocalDate.of(2026, 7, 1).atStartOfDay(zone).toInstant(),
                                LocalDate.of(2026, 7, 14).atStartOfDay(zone).toInstant(),
                                zone))
                .isEqualByComparingTo("35.33");
    }

    @Test
    void keepsRatioThresholdUnchangedForPartialPeriod() {
        var criterion = criterion("QUARTER", Aggregation.RATIO, "12");
        var zone = ZoneId.of("Europe/Sofia");

        assertThat(
                        EvaluationService.proportionalTarget(
                                criterion,
                                LocalDate.of(2026, 7, 1).atStartOfDay(zone).toInstant(),
                                LocalDate.of(2026, 7, 14).atStartOfDay(zone).toInstant(),
                                zone))
                .isEqualByComparingTo("12");
    }

    private static Criterion criterion(String cadence, Aggregation aggregation, String threshold) {
        var criterion = new Criterion();
        criterion.setPeriodType(cadence);
        criterion.setAggregation(aggregation);
        criterion.setThresholdValue(new BigDecimal(threshold));
        return criterion;
    }
}
