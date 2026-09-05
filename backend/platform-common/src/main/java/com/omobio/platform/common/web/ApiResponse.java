package com.omobio.platform.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.omobio.platform.common.tenant.TenantContext;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * Standard success response envelope.
 *
 * Every successful API response uses this shape:
 *
 *   {
 *     "data": { ... },
 *     "meta": {
 *       "timestamp": "2026-09-03T15:42:00Z",
 *       "correlationId": "omobio-a1b2c3d4",
 *       "version": "1.0"
 *     }
 *   }
 *
 * @param <T> The data type
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    T data;
    ResponseMeta meta;

    public static <T> ApiResponse<T> of(T data) {
        return ApiResponse.<T>builder()
            .data(data)
            .meta(ResponseMeta.now())
            .build();
    }

    public static <T> ApiResponse<T> of(T data, String correlationId) {
        return ApiResponse.<T>builder()
            .data(data)
            .meta(ResponseMeta.withCorrelationId(correlationId))
            .build();
    }

    /** Alias for {@link #of(Object)} — matches the common "ok" naming used across controllers. */
    public static <T> ApiResponse<T> ok(T data) {
        return of(data);
    }

    /** Alias for {@link #of(Object, String)} with explicit correlation id. */
    public static <T> ApiResponse<T> ok(T data, String correlationId) {
        return of(data, correlationId);
    }
}
