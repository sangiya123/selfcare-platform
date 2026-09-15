package com.selfcare.platform.common.resilience;

public class ProviderTimeoutException extends RuntimeException {
    public ProviderTimeoutException(String message) { super(message); }
    public ProviderTimeoutException(String message, Throwable cause) { super(message, cause); }
}
