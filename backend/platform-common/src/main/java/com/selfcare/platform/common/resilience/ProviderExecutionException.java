package com.selfcare.platform.common.resilience;

public class ProviderExecutionException extends RuntimeException {
    public ProviderExecutionException(Throwable cause) { super(cause); }
    public ProviderExecutionException(String message, Throwable cause) { super(message, cause); }
}
