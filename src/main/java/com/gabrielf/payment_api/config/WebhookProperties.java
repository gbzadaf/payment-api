package com.gabrielf.payment_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "webhook")
public record WebhookProperties(
        String url,
        String secret,
        int maxAttempts,
        long retryDelayMs

) {
}
