package com.omobio.platform.common.resilience;

public class ProviderErrorException extends RuntimeException {
    public ProviderErrorException(String message) { super(message); }
    public ProviderErrorException(String message, Throwable cause) { super(message, cause); }
}
