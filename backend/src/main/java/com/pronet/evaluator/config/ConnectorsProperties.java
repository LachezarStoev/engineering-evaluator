package com.pronet.evaluator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("connectors")
public record ConnectorsProperties(Gitlab gitlab, Jira jira, Confluence confluence) {
    public record Gitlab(String url, String token) {}

    public record Jira(String url, String email, String token) {}

    public record Confluence(String url, String email, String token) {}
}
