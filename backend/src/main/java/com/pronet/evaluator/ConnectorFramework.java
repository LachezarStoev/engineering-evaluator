package com.pronet.evaluator;

import java.time.*;
import java.util.*;

interface EngineeringConnector {
    String key();

    ConnectorHealth testConnection();

    List<IdentityCandidate> discoverUsers(String email);

    List<EvidenceInput> syncEvidence(String externalUserId, Instant from, Instant to);
}

record ConnectorHealth(boolean healthy, String message) {}

record IdentityCandidate(
        String externalUserId, String username, String email, boolean exactMatch) {}

record EvidenceInput(
        String metricKey,
        String externalId,
        Instant occurredAt,
        java.math.BigDecimal value,
        String title,
        String url,
        Map<String, Object> attributes) {}
