package com.akanksha.url_shortener.exception;

public class AliasTakenException extends RuntimeException {
    public AliasTakenException(String message) { super(message); }
}
