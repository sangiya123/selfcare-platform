package com.omobio.platform.common.resilience;

public class ProviderBulkheadFullException extends RuntimeException {
    public ProviderBulkheadFullException(String message) { super(message); }
    public ProviderBulkheadFullException(String message, Throwable cause) { super(message, cause); }
}
