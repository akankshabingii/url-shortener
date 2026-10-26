package com.akanksha.url_shortener.util;

import java.security.SecureRandom;

public final class Base62 {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    // SecureRandom, not Random: codes must not be predictable from earlier codes
    private static final SecureRandom RANDOM = new SecureRandom();

    private Base62() {}

    public static String encode(long number) {
        if (number == 0) return String.valueOf(ALPHABET.charAt(0));
        StringBuilder sb = new StringBuilder();
        while (number > 0) {
            sb.append(ALPHABET.charAt((int) (number % 62)));
            number /= 62;
        }
        return sb.reverse().toString();
    }

    public static String random(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}