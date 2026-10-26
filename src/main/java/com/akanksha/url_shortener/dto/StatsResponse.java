package com.akanksha.url_shortener.dto;

import java.time.Instant;

public record StatsResponse(
        String shortCode,
        String originalUrl,
        long clickCount,
        Instant createdAt,
        Instant expiresAt
) {}