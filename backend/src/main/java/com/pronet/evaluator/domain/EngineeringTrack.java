package com.pronet.evaluator.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "engineering_track")
@Getter
@Setter
@NoArgsConstructor
public class EngineeringTrack {
    @Id private UUID id = UUID.randomUUID();
    private String code;
    private String name;
    private String description;

    @Column(name = "icon_key")
    private String iconKey = "code";

    @Column(name = "ordinal_value")
    private int ordinalValue;

    private int version;

    @Enumerated(EnumType.STRING)
    private ConfigStatus status;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
