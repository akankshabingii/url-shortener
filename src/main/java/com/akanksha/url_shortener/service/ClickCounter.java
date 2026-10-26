package com.akanksha.url_shortener.service;

import com.akanksha.url_shortener.repository.UrlMappingRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ClickCounter {

    private static final Logger log = LoggerFactory.getLogger(ClickCounter.class);

    // link id -> clicks not yet written to MySQL
    private final ConcurrentHashMap<Long, AtomicLong> pending = new ConcurrentHashMap<>();
    private final UrlMappingRepository repository;

    public ClickCounter(UrlMappingRepository repository) {
        this.repository = repository;
    }

    public void increment(long id) {
        pending.computeIfAbsent(id, k -> new AtomicLong()).incrementAndGet();
    }

    public long pending(long id) {
        AtomicLong counter = pending.get(id);
        return counter == null ? 0 : counter.get();
    }

    @Scheduled(fixedDelayString = "${app.click-flush-ms:5000}")
    public void flush() {
        pending.forEach((id, counter) -> {
            long n = counter.getAndSet(0);   // atomic: a click is never lost or counted twice
            if (n == 0) return;
            try {
                repository.incrementClickCountBy(id, n);
            } catch (RuntimeException e) {
                counter.addAndGet(n);        // DB problem: keep the clicks, retry next time
                log.warn("Could not flush {} clicks for link {}: {}", n, id, e.getMessage());
            }
        });
    }

    // Write what is left when the app shuts down normally
    @PreDestroy
    public void flushOnShutdown() {
        flush();
    }
}