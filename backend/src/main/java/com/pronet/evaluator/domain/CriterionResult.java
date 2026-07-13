package com.pronet.evaluator.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "criterion_result")
@Getter
@Setter
@NoArgsConstructor
public class CriterionResult {
    @Id private UUID id = UUID.randomUUID();

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "evaluation_id")
    private Evaluation evaluation;

    @Column(name = "criterion_id")
    private UUID criterionId;

    @Column(name = "criterion_name")
    private String criterionName;

    @Column(name = "measured_value")
    private BigDecimal measuredValue;

    @Column(name = "threshold_value")
    private BigDecimal thresholdValue;

    @Column(name = "threshold_max_value")
    private BigDecimal thresholdMaxValue;

    @Column(name = "period_target_value")
    private BigDecimal periodTargetValue;

    @Column(name = "period_target_max_value")
    private BigDecimal periodTargetMaxValue;

    @Column(name = "result_status")
    @Enumerated(EnumType.STRING)
    private ResultStatus resultStatus;

    private String formula;
    private String coverage;
    private String cadence;

    @Column(name = "manager_decision")
    @Enumerated(EnumType.STRING)
    private ResultStatus managerDecision;

    @Column(name = "manager_note")
    private String managerNote;
}
