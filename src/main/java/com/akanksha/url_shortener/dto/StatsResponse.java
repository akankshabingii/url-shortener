package com.akanksha.url_shortener.dto;

import java.time.LocalDateTime;

public record StatsResponse(
        String shortCode,
        String originalUrl,
        long clickCount,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {}
