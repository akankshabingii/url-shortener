package com.akanksha.url_shortener.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}