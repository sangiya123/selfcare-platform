package com.omobio.platform.common.web;

/**
 * Thrown when a client sends an invalid request.
 * Maps to 400 Bad Request.
 *
 * Example: missing required field, invalid format, validation failure.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}