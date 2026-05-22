package com.wex.challenge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "treasury")
public record TreasuryProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs,
        int lookbackMonths
) {
}
