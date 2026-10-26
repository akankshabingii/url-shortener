package com.akanksha.url_shortener.service;

import com.akanksha.url_shortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClickCounterTest {

    @Mock
    private UrlMappingRepository repository;

    @Test
    void flush_writesOneBatchedUpdateAndResets() {
        ClickCounter counter = new ClickCounter(repository);
        counter.increment(7L);
        counter.increment(7L);
        counter.increment(7L);
        assertEquals(3, counter.pending(7L));

        counter.flush();

        verify(repository).incrementClickCountBy(7L, 3L);   // one UPDATE, not three
        assertEquals(0, counter.pending(7L));

        counter.flush();                                    // nothing new -> no DB call
        verifyNoMoreInteractions(repository);
    }

    @Test
    void flush_whenDbFails_keepsClicksForNextTime() {
        doThrow(new RuntimeException("db down"))
                .when(repository).incrementClickCountBy(7L, 2L);
        ClickCounter counter = new ClickCounter(repository);
        counter.increment(7L);
        counter.increment(7L);

        counter.flush();

        assertEquals(2, counter.pending(7L));               // nothing lost
    }
}