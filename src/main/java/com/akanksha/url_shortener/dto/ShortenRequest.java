package com.akanksha.url_shortener.dto;

import jakarta.validation.constraints.*;

public record ShortenRequest(
        @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url is too long")
        @Pattern(regexp = "^https?://.+", message = "url must start with http:// or https://")
        String url,

        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,30}$",
                 message = "customAlias must be 3-30 chars: letters, digits, _ or -")
        String customAlias,

        @Min(value = 1, message = "expiresInDays must be at least 1")
        @Max(value = 3650, message = "expiresInDays is too large")
        Integer expiresInDays
) {}
