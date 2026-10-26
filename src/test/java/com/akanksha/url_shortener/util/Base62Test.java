package com.akanksha.url_shortener.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Base62Test {

    @Test
    void encode_knownValues() {
        assertEquals("0", Base62.encode(0));
        assertEquals("9", Base62.encode(9));
        assertEquals("a", Base62.encode(10));
        assertEquals("Z", Base62.encode(61));
        assertEquals("10", Base62.encode(62));
    }

    @Test
    void random_hasRequestedLengthAndOnlyBase62Characters() {
        for (int i = 0; i < 200; i++) {
            String code = Base62.random(7);
            assertEquals(7, code.length());
            assertTrue(code.matches("[0-9a-zA-Z]{7}"), "bad code: " + code);
        }
    }

    @Test
    void random_producesDifferentCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(Base62.random(7));
        }
        assertEquals(1000, codes.size());
    }
}