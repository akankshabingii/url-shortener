package com.akanksha.url_shortener.service;

import com.akanksha.url_shortener.dto.ShortenRequest;
import com.akanksha.url_shortener.dto.ShortenResponse;
import com.akanksha.url_shortener.dto.StatsResponse;
import com.akanksha.url_shortener.entity.UrlMapping;
import com.akanksha.url_shortener.exception.AliasTakenException;
import com.akanksha.url_shortener.exception.BadRequestException;
import com.akanksha.url_shortener.exception.ExpiredException;
import com.akanksha.url_shortener.exception.NotFoundException;
import com.akanksha.url_shortener.repository.UrlMappingRepository;
import com.akanksha.url_shortener.util.Base62;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;

@Service
public class UrlService {

    // 62^7 = ~3.5 trillion possible codes
    private static final int CODE_LENGTH = 7;
    private static final int MAX_ATTEMPTS = 5;

    // Aliases that could clash with app endpoints or look official
    private static final Set<String> RESERVED_ALIASES =
            Set.of("api", "error", "actuator", "admin", "health", "static", "swagger-ui", "v3");

    private final UrlMappingRepository repository;
    private final LinkLookup linkLookup;
    private final ClickCounter clickCounter;
    private final String baseUrl;
    private final String baseHost;
    private final Clock clock;

    public UrlService(UrlMappingRepository repository,
                      LinkLookup linkLookup,
                      ClickCounter clickCounter,
                      @Value("${app.base-url}") String baseUrl,
                      Clock clock) {
        this.repository = repository;
        this.linkLookup = linkLookup;
        this.clickCounter = clickCounter;
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
        this.baseHost = URI.create(this.baseUrl).getHost();
        this.clock = clock;
    }

    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        validateTargetUrl(request.url());

        Instant now = clock.instant();

        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(request.url());
        mapping.setCreatedAt(now);
        if (request.expiresInDays() != null) {
            mapping.setExpiresAt(now.plus(request.expiresInDays(), ChronoUnit.DAYS));
        }

        String code;
        if (request.customAlias() != null && !request.customAlias().isBlank()) {
            String alias = request.customAlias();
            if (RESERVED_ALIASES.contains(alias.toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("That alias is reserved");
            }
            if (repository.existsByShortCode(alias)) {
                throw new AliasTakenException("Alias already in use");
            }
            code = alias;
        } else {
            code = generateUniqueCode();
        }

        mapping.setShortCode(code);
        repository.save(mapping);   // one INSERT; the unique index is the final safety net

        return new ShortenResponse(
                mapping.getShortCode(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getOriginalUrl(),
                mapping.getExpiresAt());
    }

    // Hot path: no transaction, usually no DB access at all.
    public String resolveAndCount(String shortCode) {
        LinkTarget target = linkLookup.find(shortCode);          // cached
        if (target.isExpired(clock.instant())) {                 // checked every time
            throw new ExpiredException("This short URL has expired");
        }
        clickCounter.increment(target.id());                     // in memory, flushed in batches
        return target.originalUrl();
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(String shortCode) {
        UrlMapping m = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new NotFoundException("Short URL not found"));
        // saved clicks + clicks still waiting to be flushed
        long clicks = m.getClickCount() + clickCounter.pending(m.getId());
        return new StatsResponse(m.getShortCode(), m.getOriginalUrl(),
                clicks, m.getCreatedAt(), m.getExpiresAt());
    }

    // Random code; if it already exists (very rare), try again.
    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = Base62.random(CODE_LENGTH);
            if (!repository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique short code");
    }

    private void validateTargetUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new BadRequestException("url is not a valid URL (check for spaces or illegal characters)");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BadRequestException("url must contain a valid host");
        }
        if (host.equalsIgnoreCase(baseHost)) {
            throw new BadRequestException("url cannot point to this service");
        }
    }
}