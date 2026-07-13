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

    private String operator;

    @Column(name = "threshold_value")
    private BigDecimal thresholdValue;

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
