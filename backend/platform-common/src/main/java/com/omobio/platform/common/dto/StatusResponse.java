package com.omobio.platform.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Generic status response for any operation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusResponse {

    private String status;
    private String message;
    private String correlationId;
    private Map<String, Object> metadata;

    public static StatusResponse success() {
        return new StatusResponse("SUCCESS", null, null, null);
    }

    public static StatusResponse success(String message) {
        return new StatusResponse("SUCCESS", message, null, null);
    }

    public static StatusResponse pending() {
        return new StatusResponse("PENDING", null, null, null);
    }

    public static StatusResponse failed(String reason) {
        return new StatusResponse("FAILED", reason, null, null);
    }
}