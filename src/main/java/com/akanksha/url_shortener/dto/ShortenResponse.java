package com.akanksha.url_shortener.dto;

import java.time.LocalDateTime;

public record ShortenResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        LocalDateTime expiresAt
) {}
