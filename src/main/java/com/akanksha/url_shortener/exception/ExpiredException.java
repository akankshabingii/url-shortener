package com.akanksha.url_shortener.exception;

public class ExpiredException extends RuntimeException {
    public ExpiredException(String message) { super(message); }
}
