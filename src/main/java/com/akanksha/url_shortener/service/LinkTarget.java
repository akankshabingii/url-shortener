package com.akanksha.url_shortener.service;

import java.time.Instant;

// What the redirect path needs, and nothing more. This is what gets cached.
public record LinkTarget(long id, String originalUrl, Instant expiresAt) {

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}