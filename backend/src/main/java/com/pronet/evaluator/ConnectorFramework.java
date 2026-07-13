package com.pronet.evaluator;

import java.time.*;
import java.util.*;

interface EngineeringConnector {
    String key();

    ConnectorHealth testConnection();

    List<IdentityCandidate> discoverUsers(String email);

    List<EvidenceInput> syncEvidence(String externalUserId, Instant from, Instant to);
}

record ConnectorHealth(boolean healthy, String message, String status) {
    ConnectorHealth(boolean healthy, String message) {
        this(healthy, message, healthy ? "CONNECTED" : "SOURCE_UNAVAILABLE");
    }
}

record IdentityCandidate(
        String externalUserId,
        String username,
        String email,
        boolean exactMatch,
        IdentityMatchType matchType,
        int confidence) {
    IdentityCandidate(String externalUserId, String username, String email, boolean exactMatch) {
        this(
                externalUserId,
                username,
                email,
                exactMatch,
                exactMatch ? IdentityMatchType.EXACT_EMAIL : IdentityMatchType.UNVERIFIED,
                exactMatch ? 100 : 0);
    }
}

enum IdentityMatchType {
    EXACT_EMAIL,
    UNIQUE_NAME,
    REUSED_ATLASSIAN,
    UNVERIFIED
}

record EvidenceInput(
        String metricKey,
        String externalId,
        Instant occurredAt,
        java.math.BigDecimal value,
        String title,
        String url,
        Map<String, Object> attributes) {}
