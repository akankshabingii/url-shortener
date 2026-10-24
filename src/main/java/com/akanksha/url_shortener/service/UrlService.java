package com.akanksha.url_shortener.service;

import com.akanksha.url_shortener.dto.ShortenRequest;
import com.akanksha.url_shortener.dto.ShortenResponse;
import com.akanksha.url_shortener.dto.StatsResponse;
import com.akanksha.url_shortener.entity.UrlMapping;
import com.akanksha.url_shortener.exception.AliasTakenException;
import com.akanksha.url_shortener.exception.ExpiredException;
import com.akanksha.url_shortener.exception.NotFoundException;
import com.akanksha.url_shortener.repository.UrlMappingRepository;
import com.akanksha.url_shortener.util.Base62;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UrlService {

    // Offset so generated codes start at ~6 characters instead of "1", "2", ...
    private static final long ID_OFFSET = 10_000_000_000L;

    private final UrlMappingRepository repository;
    private final String baseUrl;

    public UrlService(UrlMappingRepository repository,
                      @Value("${app.base-url}") String baseUrl) {
        this.repository = repository;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(request.url());
        mapping.setCreatedAt(LocalDateTime.now());
        if (request.expiresInDays() != null) {
            mapping.setExpiresAt(LocalDateTime.now().plusDays(request.expiresInDays()));
        }

        if (request.customAlias() != null && !request.customAlias().isBlank()) {
            if (repository.existsByShortCode(request.customAlias())) {
                throw new AliasTakenException("Alias already in use");
            }
            mapping.setShortCode(request.customAlias());
            repository.save(mapping);
        } else {
            // 1) insert -> MySQL gives us a unique auto-increment id
            repository.save(mapping);
            // 2) short code = Base62(id): unique by construction, no collision checks
            mapping.setShortCode(Base62.encode(mapping.getId() + ID_OFFSET));
            // entity is managed, so this update is flushed on commit
        }

        return new ShortenResponse(
                mapping.getShortCode(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getOriginalUrl(),
                mapping.getExpiresAt());
    }

    @Transactional
    public String resolveAndCount(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new NotFoundException("Short URL not found"));
        if (mapping.isExpired()) {
            throw new ExpiredException("This short URL has expired");
        }
        repository.incrementClickCount(mapping.getId());
        return mapping.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(String shortCode) {
        UrlMapping m = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new NotFoundException("Short URL not found"));
        return new StatsResponse(m.getShortCode(), m.getOriginalUrl(),
                m.getClickCount(), m.getCreatedAt(), m.getExpiresAt());
    }
}
