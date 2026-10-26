package com.akanksha.url_shortener.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "url_mapping")
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    // unique => MySQL builds an index, so lookups by code are fast.
    // utf8mb4_bin makes the column case-sensitive (this is the Step 1 fix),
    // so "aB" and "Ab" are treated as different codes.
    @Column(unique = true, length = 30,
            columnDefinition = "varchar(30) character set utf8mb4 collate utf8mb4_bin")
    private String shortCode;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant expiresAt;

    @Column(nullable = false)
    private long clickCount = 0;

    public Long getId() { return id; }
    public String getOriginalUrl() { return originalUrl; }
    public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }
    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public long getClickCount() { return clickCount; }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}