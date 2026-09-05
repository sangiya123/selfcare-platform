package com.omobio.dashboard.web.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies the WidgetResult factory methods and the partial-response contract
 * defined in ADR-008 (one slow widget must NOT block the dashboard).
 */
class WidgetResultTest {

    @Test
    @DisplayName("success() returns SUCCESS with data and no error")
    void success() {
        WidgetResult r = WidgetResult.success("balance", "data");
        assertThat(r.getStatus()).isEqualTo("SUCCESS");
        assertThat(r.getData()).isEqualTo("data");
        assertThat(r.getErrorMessage()).isNull();
        assertThat(r.getRetryable()).isFalse();
    }

    @Test
    @DisplayName("timeout() returns TIMEOUT with elapsed ms and retryable=true")
    void timeout() {
        WidgetResult r = WidgetResult.timeout("balance", 2000);
        assertThat(r.getStatus()).isEqualTo("TIMEOUT");
        assertThat(r.getElapsedMs()).isEqualTo(2000);
        assertThat(r.getRetryable()).isTrue();
        assertThat(r.getErrorMessage()).contains("timed out");
    }

    @Test
    @DisplayName("partial() marks status=PARTIAL with reason and retryable=true")
    void partial() {
        WidgetResult r = WidgetResult.partial("balance", "fallback", "Provider slow");
        assertThat(r.getStatus()).isEqualTo("PARTIAL");
        assertThat(r.getData()).isEqualTo("fallback");
        assertThat(r.getErrorMessage()).isEqualTo("Provider slow");
        assertThat(r.getRetryable()).isTrue();
    }

    @Test
    @DisplayName("stale() marks data as potentially stale, retryable=true")
    void stale() {
        WidgetResult r = WidgetResult.stale("balance", "stale-data");
        assertThat(r.getStatus()).isEqualTo("STALE");
        assertThat(r.getRetryable()).isTrue();
    }

    @Test
    @DisplayName("unavailable() is NOT retryable (e.g., user has no policy)")
    void unavailable() {
        WidgetResult r = WidgetResult.unavailable("insurance", "no policy");
        assertThat(r.getStatus()).isEqualTo("UNAVAILABLE");
        assertThat(r.getRetryable()).isFalse();
    }

    @Test
    @DisplayName("error() with code+message preserves both")
    void error() {
        WidgetResult r = WidgetResult.error("balance", "PROVIDER_DOWN", "BSS unreachable");
        assertThat(r.getStatus()).isEqualTo("ERROR");
        assertThat(r.getErrorCode()).isEqualTo("PROVIDER_DOWN");
        assertThat(r.getErrorMessage()).isEqualTo("BSS unreachable");
        assertThat(r.getRetryable()).isTrue();
    }
}
