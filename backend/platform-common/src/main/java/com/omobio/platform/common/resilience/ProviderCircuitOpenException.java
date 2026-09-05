package com.omobio.platform.common.resilience;

public class ProviderCircuitOpenException extends RuntimeException {
    public ProviderCircuitOpenException(String message) { super(message); }
    public ProviderCircuitOpenException(String message, Throwable cause) { super(message, cause); }
}
