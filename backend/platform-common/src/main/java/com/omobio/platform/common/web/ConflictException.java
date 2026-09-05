package com.omobio.platform.common.web;

/**
 * Thrown when a request conflicts with current state.
 * Maps to 409 Conflict.
 *
 * Example: duplicate creation, idempotency conflict, version mismatch.
 */
public class ConflictException extends RuntimeException {
    private final String code;

    public ConflictException(String message) {
        super(message);
        this.code = null;
    }

    public ConflictException(String code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
    }

    public String getCode() {
        return code;
    }
}