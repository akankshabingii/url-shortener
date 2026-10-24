package com.akanksha.url_shortener.util;

public final class Base62 {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

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
}
