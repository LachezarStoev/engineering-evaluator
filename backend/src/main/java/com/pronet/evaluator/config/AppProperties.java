package com.pronet.evaluator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(String timeZone, Security security, Sync sync, Ai ai) {
    public record Security(boolean devMode) {}

    public record Sync(String cron) {}

    public record Ai(String baseUrl, String apiKey, String model) {}
}
