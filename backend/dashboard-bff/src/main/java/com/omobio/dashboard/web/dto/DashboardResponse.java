package com.omobio.dashboard.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Complete dashboard response from the orchestrator.
 *
 * {
 *   "correlationId": "omobio-a1b2c3d4",
 *   "elapsedMs": 412,
 *   "overallStatus": "SUCCESS",
 *   "widgets": {
 *     "balance": {
 *       "widgetId": "balance",
 *       "status": "SUCCESS",
 *       "data": { "amount": 1250.00, "currency": "LKR", ... },
 *       "elapsedMs": 87
 *     },
 *     "usage": {
 *       "widgetId": "usage",
 *       "status": "SUCCESS",
 *       "data": { "dataAllowance": {...}, "voiceAllowance": {...} },
 *       "elapsedMs": 120
 *     },
 *     "offers": {
 *       "widgetId": "offers",
 *       "status": "PARTIAL",
 *       "data": [...],
 *       "errorMessage": "Some offers filtered due to eligibility"
 *     },
 *     "bill": {
 *       "widgetId": "bill",
 *       "status": "TIMEOUT",
 *       "errorMessage": "Widget timed out after 300ms"
 *     }
 *   },
 *   "timestamp": "2026-09-03T15:42:00Z"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private String correlationId;
    private Long elapsedMs;
    private String overallStatus;
    private Map<String, WidgetResult> widgets;
    private Instant timestamp;
}