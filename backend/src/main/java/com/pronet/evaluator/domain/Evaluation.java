package com.pronet.evaluator.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "evaluation")
@Getter
@Setter
@NoArgsConstructor
public class Evaluation {
    @Id private UUID id = UUID.randomUUID();

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(length = 64)
    private String period;

    @Column(name = "level_code")
    private String levelCode;

    @Column(name = "rule_version")
    private int ruleVersion;

    @Column(name = "period_from")
    private Instant periodFrom;

    @Column(name = "period_to")
    private Instant periodTo;

    @Column(name = "period_timezone")
    private String periodTimezone = "Europe/Sofia";

    @Enumerated(EnumType.STRING)
    private EvaluationStatus status;

    private boolean finalized;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @OneToMany(
            mappedBy = "evaluation",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<CriterionResult> results = new ArrayList<>();
}
