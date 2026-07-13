package com.pronet.evaluator.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "competency_expectation")
@Getter
@Setter
@NoArgsConstructor
public class CompetencyExpectation {
    @Id private UUID id = UUID.randomUUID();

    @Column(name = "competency_key")
    private String competencyKey;

    @Column(name = "level_code")
    private String levelCode;

    @Column(name = "track_code")
    private String trackCode;

    private String expectation;

    @Column(name = "rubric_text")
    private String rubricText;

    private int version;
}
