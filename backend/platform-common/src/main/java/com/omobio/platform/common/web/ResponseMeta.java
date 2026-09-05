package com.omobio.platform.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.omobio.platform.common.tenant.TenantContext;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * Metadata included in every API response.
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseMeta {

    String timestamp;
    String correlationId;
    @Builder.Default
    String version = "1.0";

    public static ResponseMeta now() {
        return ResponseMeta.builder()
            .timestamp(Instant.now().toString())
            .correlationId(TenantContext.get().getCorrelationId())
            .build();
    }

    public static ResponseMeta withCorrelationId(String correlationId) {
        return ResponseMeta.builder()
            .timestamp(Instant.now().toString())
            .correlationId(correlationId != null ? correlationId : TenantContext.get().getCorrelationId())
            .build();
    }
}
