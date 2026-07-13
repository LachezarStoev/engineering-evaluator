package com.pronet.evaluator.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "audit_event")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {
    @Id private UUID id = UUID.randomUUID();

    @Column(name = "actor_email")
    private String actorEmail;

    private String action;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "details_json")
    private String detailsJson = "{}";

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
