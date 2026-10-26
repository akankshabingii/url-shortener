package com.akanksha.url_shortener.service;

import com.akanksha.url_shortener.dto.ShortenRequest;
import com.akanksha.url_shortener.dto.ShortenResponse;
import com.akanksha.url_shortener.entity.UrlMapping;
import com.akanksha.url_shortener.exception.*;
import com.akanksha.url_shortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");

    @Mock private UrlMappingRepository repository;
    @Mock private LinkLookup linkLookup;
    @Mock private ClickCounter clickCounter;

    private UrlService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);   // time never changes in tests
        service = new UrlService(repository, linkLookup, clickCounter,
                "http://localhost:8080", fixed);
    }

    @Test
    void shorten_generatesCodeAndExpiry() {
        ShortenResponse res = service.shorten(
                new ShortenRequest("https://example.com", null, 2));

        assertEquals(7, res.shortCode().length());
        assertEquals("http://localhost:8080/" + res.shortCode(), res.shortUrl());
        assertEquals(NOW.plusSeconds(2 * 24 * 3600), res.expiresAt());
        verify(repository).save(any(UrlMapping.class));
    }

    @Test
    void shorten_noExpiry_whenNotRequested() {
        ShortenResponse res = service.shorten(
                new ShortenRequest("https://example.com", null, null));
        assertNull(res.expiresAt());
    }

    @Test
    void shorten_customAliasTaken_throwsAndSavesNothing() {
        when(repository.existsByShortCode("mylink")).thenReturn(true);

        assertThrows(AliasTakenException.class, () -> service.shorten(
                new ShortenRequest("https://example.com", "mylink", null)));
        verify(repository, never()).save(any());
    }

    @Test
    void shorten_reservedAlias_rejectedInAnyCase() {
        assertThrows(BadRequestException.class, () -> service.shorten(
                new ShortenRequest("https://example.com", "API", null)));
    }

    @Test
    void shorten_urlWithSpace_rejected() {
        assertThrows(BadRequestException.class, () -> service.shorten(
                new ShortenRequest("https://example.com/a b", null, null)));
    }

    @Test
    void shorten_urlPointingToOwnService_rejected() {
        assertThrows(BadRequestException.class, () -> service.shorten(
                new ShortenRequest("http://localhost:8080/abc", null, null)));
    }

    @Test
    void resolve_unknownCode_throwsNotFound() {
        when(linkLookup.find("nope")).thenThrow(new NotFoundException("Short URL not found"));
        assertThrows(NotFoundException.class, () -> service.resolveAndCount("nope"));
    }

    @Test
    void resolve_expiredLink_throwsAndDoesNotCountClick() {
        when(linkLookup.find("old"))
                .thenReturn(new LinkTarget(5L, "https://example.com", NOW.minusSeconds(60)));

        assertThrows(ExpiredException.class, () -> service.resolveAndCount("old"));
        verify(clickCounter, never()).increment(anyLong());
    }

    @Test
    void resolve_validLink_countsClickAndReturnsTarget() {
        when(linkLookup.find("ok"))
                .thenReturn(new LinkTarget(5L, "https://example.com", NOW.plusSeconds(60)));

        assertEquals("https://example.com", service.resolveAndCount("ok"));
        verify(clickCounter).increment(5L);
    }

    @Test
    void stats_includesClicksNotYetFlushed() {
        UrlMapping m = mapping(5L, 10L);
        when(repository.findByShortCode("ok")).thenReturn(Optional.of(m));
        when(clickCounter.pending(5L)).thenReturn(3L);

        assertEquals(13L, service.stats("ok").clickCount());
    }

    private UrlMapping mapping(Long id, long clicks) {
        UrlMapping m = new UrlMapping();
        ReflectionTestUtils.setField(m, "id", id);            // entity has no setId
        ReflectionTestUtils.setField(m, "clickCount", clicks);
        m.setShortCode("ok");
        m.setOriginalUrl("https://example.com");
        m.setCreatedAt(NOW);
        return m;
    }
}