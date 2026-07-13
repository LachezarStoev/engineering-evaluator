package com.pronet.evaluator.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "engineering_level")
@Getter
@Setter
@NoArgsConstructor
public class EngineeringLevel {
    @Id private UUID id = UUID.randomUUID();
    private String code;
    private String name;

    @Column(name = "ordinal_value")
    private int ordinalValue;

    private int version;

    @Enumerated(EnumType.STRING)
    private ConfigStatus status;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;
}
