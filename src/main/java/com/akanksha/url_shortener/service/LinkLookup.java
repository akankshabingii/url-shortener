package com.akanksha.url_shortener.service;

import com.akanksha.url_shortener.exception.NotFoundException;
import com.akanksha.url_shortener.repository.UrlMappingRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LinkLookup {

    private final UrlMappingRepository repository;

    public LinkLookup(UrlMappingRepository repository) {
        this.repository = repository;
    }

    // First call for a code hits MySQL; later calls are served from memory.
    // A NotFoundException is not cached, so a link created later is found immediately.
    @Cacheable(cacheNames = "links", key = "#shortCode")
    @Transactional(readOnly = true)
    public LinkTarget find(String shortCode) {
        return repository.findByShortCode(shortCode)
                .map(m -> new LinkTarget(m.getId(), m.getOriginalUrl(), m.getExpiresAt()))
                .orElseThrow(() -> new NotFoundException("Short URL not found"));
    }
}