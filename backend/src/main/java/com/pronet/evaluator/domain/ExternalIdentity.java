package com.pronet.evaluator.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "external_identity")
@Getter
@Setter
@NoArgsConstructor
public class ExternalIdentity {
    @Id private UUID id = UUID.randomUUID();

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "tool_key")
    private String toolKey;

    @Column(name = "external_user_id")
    private String externalUserId;

    private String username;

    @Column(name = "matched_email")
    private String matchedEmail;

    private boolean verified;
}
