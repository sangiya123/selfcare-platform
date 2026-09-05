package com.omobio.platform.common.web;

/**
 * Thrown when authentication is missing or invalid.
 * Maps to 401 Unauthorized.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}