package com.pronet.evaluator.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "dispute")
@Getter
@Setter
@NoArgsConstructor
public class Dispute {
    @Id private UUID id = UUID.randomUUID();

    @Column(name = "evaluation_id")
    private UUID evaluationId;

    @Column(name = "criterion_result_id")
    private UUID criterionResultId;

    @Column(name = "author_email")
    private String authorEmail;

    private String message;
    private String status = "OPEN";

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
