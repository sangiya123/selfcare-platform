package com.selfcare.platform.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link UrlAllowlist}.
 *
 * Verifies that:
 * - Allowed hostnames pass
 * - Blocked private/reserved IPs are rejected
 * - Non-allowlist hostnames are rejected
 * - Malformed URLs are rejected
 * - Explicitly configured mock/private hosts are trusted at request time
 */
class UrlAllowlistTest {

    @AfterEach
    void clearConfiguredHosts() {
        UrlAllowlist.reset();
    }

    @Test
    @DisplayName("Allows standard AI provider hostnames")
    void allowsAiProviders() {
        UrlAllowlist.validate("https://api.anthropic.com/v1/messages");
        UrlAllowlist.validate("https://api.openai.com/v1/chat/completions");
        UrlAllowlist.validate("https://generativelanguage.googleapis.com/v1/models");
    }

    @Test
    @DisplayName("Allows operator BSS hostnames")
    void allowsOperatorBss() {
        UrlAllowlist.validate("https://bss.dialog.lk/api/balance");
        UrlAllowlist.validate("https://bss.hutch.lk/api/profile");
        UrlAllowlist.validate("https://bss.airtel.lk/api/payment");
    }

    @Test
    @DisplayName("Allows subdomain of an allowed host")
    void allowsSubdomain() {
        UrlAllowlist.validate("https://eu.api.openai.com/v1/chat");
    }

    @Test
    @DisplayName("Rejects URL with no host")
    void rejectsNoHost() {
        assertThatThrownBy(() -> UrlAllowlist.validate("https:///foo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
    }

    @Test
    @DisplayName("Rejects URL with non-http scheme")
    void rejectsNonHttp() {
        assertThatThrownBy(() -> UrlAllowlist.validate("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scheme");
    }

    @Test
    @DisplayName("Rejects URL pointing to a private/reserved IP")
    void rejectsPrivateIp() {
        // Loopback IPv4
        assertThatThrownBy(() -> UrlAllowlist.validate("https://127.0.0.1/api"))
                .isInstanceOf(IllegalArgumentException.class);
        // AWS metadata endpoint
        assertThatThrownBy(() -> UrlAllowlist.validate("https://169.254.169.254/latest/meta-data"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects host not in allow list")
    void rejectsNotInAllowList() {
        assertThatThrownBy(() -> UrlAllowlist.validate("https://evil.example.com/foo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allow list");
    }

    @Test
    @DisplayName("Custom allow list is respected")
    void customAllowList() {
        // Custom list with a different host
        UrlAllowlist.validate("https://api.anthropic.com/v1",
                java.util.Arrays.asList("api.anthropic.com"));
        // Original host is now rejected with the custom list
        assertThatThrownBy(() -> UrlAllowlist.validate("https://api.openai.com/v1",
                java.util.Arrays.asList("api.anthropic.com")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Empty/null URL is rejected")
    void rejectsNull() {
        assertThatThrownBy(() -> UrlAllowlist.validate(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlAllowlist.validate(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("defaultAllowedHosts returns the default set")
    void defaultAllowedHosts() {
        assertThat(UrlAllowlist.defaultAllowedHosts())
                .contains("api.anthropic.com", "api.openai.com", "bss.dialog.lk");
    }

    @Test
    @DisplayName("Configured mock/private hosts are trusted at request time")
    void configuredHostsAreTrusted() {
        // localhost is not banded-in default → blocked by the hostname allow list
        assertThatThrownBy(() -> UrlAllowlist.validate("http://localhost:9999/oauth/token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allow list");

        // Once the operator explicitly trusts it, the request-time check passes
        // even though localhost resolves to a loopback address.
        UrlAllowlist.configure(java.util.List.of("localhost"));
        UrlAllowlist.validate("http://localhost:9999/oauth/token");
    }

    @Test
    @DisplayName("Admin/config-write path still rejects private IPs for configured hosts")
    void configuredHostsStayStrictForExplicitValidate() {
        UrlAllowlist.configure(java.util.List.of("localhost"));
        assertThatThrownBy(() -> UrlAllowlist.validate("http://localhost:9999/oauth/token",
                java.util.List.of("localhost")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private");
    }

    @Test
    @DisplayName("effectiveAllowedHosts merges defaults and configured hosts")
    void effectiveHostsMerge() {
        UrlAllowlist.configure(java.util.List.of("localhost", "bss-dialog.svc"));
        assertThat(UrlAllowlist.effectiveAllowedHosts())
                .contains("api.anthropic.com", "bss.dialog.lk", "localhost", "bss-dialog.svc");
        assertThat(UrlAllowlist.defaultAllowedHosts())
                .doesNotContain("localhost", "bss-dialog.svc");
    }
}
