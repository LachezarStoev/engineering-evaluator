package com.pronet.evaluator.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "connector_config")
@Getter
@Setter
@NoArgsConstructor
public class ConnectorConfig {
    @Id private UUID id = UUID.randomUUID();

    @Column(name = "tool_key", unique = true)
    private String toolKey;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "base_url")
    private String baseUrl;

    private boolean enabled;

    @Column(name = "allowed_scopes")
    private String allowedScopes;

    @Column(name = "health_status")
    private String healthStatus = "NOT_CONFIGURED";

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;
}
