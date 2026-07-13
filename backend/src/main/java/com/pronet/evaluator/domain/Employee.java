package com.pronet.evaluator.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "employee")
@Getter
@Setter
@NoArgsConstructor
public class Employee {
    @Id private UUID id = UUID.randomUUID();

    @Column(name = "canonical_email", nullable = false, unique = true)
    private String canonicalEmail;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String team;

    @Column(name = "manager_email")
    private String managerEmail;

    @Column(name = "current_level_code")
    private String currentLevelCode;

    @Column(name = "target_level_code")
    private String targetLevelCode;

    @Column(name = "track_code")
    private String trackCode = "GENERAL";

    @Column(name = "employment_start")
    private LocalDate employmentStart;

    @Column(name = "probation_end")
    private LocalDate probationEnd;

    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "employee_alias", joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "email")
    private Set<String> aliases = new HashSet<>();
}
