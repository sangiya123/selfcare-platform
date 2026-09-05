package com.omobio.dashboard.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Result of a single dashboard widget execution.
 *
 * Status codes:
 *   SUCCESS    — data returned successfully
 *   PARTIAL    — partial data returned (e.g., some items filtered)
 *   STALE      — cached/stale data returned
 *   TIMEOUT    — widget timed out
 *   UNAVAILABLE — widget not available for this user/context
 *   ERROR      — widget threw an exception
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WidgetResult {

    private String widgetId;
    private String status;          // SUCCESS, PARTIAL, STALE, TIMEOUT, UNAVAILABLE, ERROR
    private Object data;            // Widget data payload
    private String errorMessage;    // Error/reason when not SUCCESS
    private Long elapsedMs;        // Execution time in ms
    private Boolean retryable;      // Can this widget be retried?
    private String errorCode;       // Machine-readable error code
    private Map<String, String> metadata; // Additional metadata

    // Factory methods
    public static WidgetResult success(String widgetId, Object data) {
        return WidgetResult.builder()
                .widgetId(widgetId)
                .status("SUCCESS")
                .data(data)
                .retryable(false)
                .build();
    }

    public static WidgetResult success(String widgetId, Object data, long elapsedMs) {
        return WidgetResult.builder()
                .widgetId(widgetId)
                .status("SUCCESS")
                .data(data)
                .elapsedMs(elapsedMs)
                .retryable(false)
                .build();
    }

    public static WidgetResult partial(String widgetId, Object data, String reason) {
        return WidgetResult.builder()
                .widgetId(widgetId)
                .status("PARTIAL")
                .data(data)
                .errorMessage(reason)
                .retryable(true)
                .build();
    }

    public static WidgetResult stale(String widgetId, Object data) {
        return WidgetResult.builder()
                .widgetId(widgetId)
                .status("STALE")
                .data(data)
                .errorMessage("Data may be stale")
                .retryable(true)
                .build();
    }

    public static WidgetResult timeout(String widgetId, long timeoutMs) {
        return WidgetResult.builder()
                .widgetId(widgetId)
                .status("TIMEOUT")
                .errorMessage("Widget timed out after " + timeoutMs + "ms")
                .elapsedMs(timeoutMs)
                .retryable(true)
                .build();
    }

    public static WidgetResult unavailable(String widgetId, String reason) {
        return WidgetResult.builder()
                .widgetId(widgetId)
                .status("UNAVAILABLE")
                .errorMessage(reason)
                .retryable(false)
                .build();
    }

    public static WidgetResult error(String widgetId, String errorMessage) {
        return WidgetResult.builder()
                .widgetId(widgetId)
                .status("ERROR")
                .errorMessage(errorMessage)
                .retryable(true)
                .build();
    }

    public static WidgetResult error(String widgetId, String errorCode, String errorMessage) {
        return WidgetResult.builder()
                .widgetId(widgetId)
                .status("ERROR")
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .retryable(true)
                .build();
    }
}