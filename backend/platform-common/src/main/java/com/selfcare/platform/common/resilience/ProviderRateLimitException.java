package com.selfcare.platform.common.resilience;

public class ProviderRateLimitException extends RuntimeException {
    public ProviderRateLimitException(String message) { super(message); }
    public ProviderRateLimitException(String message, Throwable cause) { super(message, cause); }
}
