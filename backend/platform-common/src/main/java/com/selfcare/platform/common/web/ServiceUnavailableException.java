package com.selfcare.platform.common.web;

/**
 * Thrown when a downstream service or provider is unavailable.
 * Maps to 503 Service Unavailable.
 *
 * Example: legacy operator API is down, provider timeout, circuit breaker open.
 */
public class ServiceUnavailableException extends RuntimeException {

    private final String provider;
    private final boolean retryable;

    public ServiceUnavailableException(String message) {
        super(message);
        this.provider = null;
        this.retryable = true;
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.provider = null;
        this.retryable = true;
    }

    public ServiceUnavailableException(String provider, String message, boolean retryable) {
        super(String.format("Provider '%s' unavailable: %s", provider, message));
        this.provider = provider;
        this.retryable = retryable;
    }

    public String getProvider() {
        return provider;
    }

    public boolean isRetryable() {
        return retryable;
    }
}