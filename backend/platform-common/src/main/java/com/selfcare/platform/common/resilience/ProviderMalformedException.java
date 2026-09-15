package com.selfcare.platform.common.resilience;

public class ProviderMalformedException extends RuntimeException {
    public ProviderMalformedException(String message) { super(message); }
    public ProviderMalformedException(String message, Throwable cause) { super(message, cause); }
}
