package com.pronet.evaluator.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "criterion")
@Getter
@Setter
@NoArgsConstructor
public class Criterion {
    @Id private UUID id = UUID.randomUUID();
    private String code;
    private String name;
    private String description;

    @Column(name = "source_tool")
    private String sourceTool;

    @Column(name = "metric_key")
    private String metricKey;

    @Column(name = "evaluation_type")
    @Enumerated(EnumType.STRING)
    private EvaluationType evaluationType;

    @Column(name = "period_type")
    private String periodType;

    @Column(name = "minimum_coverage")
    private String minimumCoverage = "COMPLETE";

    @Column(name = "custom_period_allowed")
    private boolean customPeriodAllowed;

    @Enumerated(EnumType.STRING)
    @Column(name = "criterion_scope")
    private CriterionScope scope = CriterionScope.COMMON;

    @Column(name = "track_code")
    private String trackCode;

    @Column(name = "team_key")
    private String teamKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "proration_policy")
    private ProrationPolicy prorationPolicy = ProrationPolicy.PROGRESS_ONLY;

    @Column(name = "mandatory_criterion")
    private boolean mandatory = true;

    @Column(name = "rubric_text")
    private String rubric;

    @Column(name = "visualization_key")
    private String visualization = "PROGRESS";

    private String operator;

    @Column(name = "threshold_value")
    private BigDecimal thresholdValue;

    @Column(name = "threshold_max_value")
    private BigDecimal thresholdMaxValue;

    @Enumerated(EnumType.STRING)
    private Aggregation aggregation = Aggregation.SUM;

    @Column(name = "denominator_metric_key")
    private String denominatorMetricKey;

    @Column(name = "level_code")
    private String levelCode;

    private int version;

    @Enumerated(EnumType.STRING)
    private ConfigStatus status;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;
}
