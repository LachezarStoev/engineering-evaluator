package com.pronet.evaluator.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "evidence")
@Getter
@Setter
@NoArgsConstructor
public class Evidence {
    @Id private UUID id = UUID.randomUUID();

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "tool_key")
    private String toolKey;

    @Column(name = "metric_key")
    private String metricKey;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "numeric_value")
    private BigDecimal numericValue;

    private String title;
    private String url;

    @Column(name = "attributes_json")
    private String attributesJson = "{}";

    private boolean included = true;

    @Column(name = "exclusion_reason")
    private String exclusionReason;
}
