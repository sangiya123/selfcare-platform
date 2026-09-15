package com.selfcare.platform.common.resilience;

/**
 * Provider orchestration exception types. Each maps to a specific
 * failure mode and is handled by {@link ProviderExecutor}.
 *
 * All extend RuntimeException so they can propagate through
 * non-blocking reactive pipelines (WebFlux/Reactor).
 */
public class ProviderException extends RuntimeException {
    public ProviderException(String message) { super(message); }
    public ProviderException(String message, Throwable cause) { super(message, cause); }
}
