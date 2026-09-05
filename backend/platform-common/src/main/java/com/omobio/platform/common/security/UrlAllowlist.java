package com.omobio.platform.common.security;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * URL allowlist validator for outbound integrations.
 *
 * Prevents SSRF (Server-Side Request Forgery) when an admin configures a
 * tenant's external endpoint (operator BSS, AI provider, payment gateway)
 * and the platform makes a request to that URL on behalf of the user.
 *
 * Two layers of protection:
 *  1. Hostname must match the platform allow list (configurable per tenant,
 *     with sensible defaults for AI providers and operator BSS endpoints)
 *  2. IP address of the resolved hostname must NOT be in a private/reserved
 *     range (loopback, link-local, site-local, etc.) — prevents 127.0.0.1,
 *     169.254.169.254, 10.0.0.0/8, 192.168.0.0/16, etc.
 *
 * Per the security spec: "SSRF protection for configurable endpoints"
 */
@Slf4j
public final class UrlAllowlist {

    /**
     * Default allow list of hostnames (suffix match). Add your operator BSS
     * base URLs and AI provider endpoints here. Production should override
     * via configuration.
     */
    public static final List<String> DEFAULT_ALLOWED_HOSTS = Arrays.asList(
            // Anthropic
            "api.anthropic.com",
            // OpenAI
            "api.openai.com",
            // Google AI
            "generativelanguage.googleapis.com",
            "aiplatform.googleapis.com",
            // Common BSS vendors
            "bss.dialog.lk",
            "bss.hutch.lk",
            "bss.airtel.lk",
            "aia.lk",
            "aia.com.lk"
    );

    /** IP ranges considered private/reserved — request refused. */
    private static final List<String> BLOCKED_IP_PREFIXES = Arrays.asList(
            "127.",          // loopback
            "10.",           // site-local (RFC 1918)
            "192.168.",      // site-local (RFC 1918)
            "169.254.",      // link-local (incl. AWS metadata 169.254.169.254)
            "0.",            // unspecified
            "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.",
            "172.24.", "172.25.", "172.26.", "172.27.",
            "172.28.", "172.29.", "172.30.", "172.31.",
            "::1",           // IPv6 loopback
            "fc00:", "fd00:", // IPv6 ULA
            "fe80:"          // IPv6 link-local
    );

    private static final Pattern SCHEME_PATTERN = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);

    private UrlAllowlist() {}

    /**
     * Validate that a URL is safe to call. Throws if the URL fails any check.
     *
     * @param url the URL to validate
     * @param allowedHosts the list of allowed hostnames (suffix match)
     * @throws IllegalArgumentException if the URL is not allowed
     */
    public static void validate(String url, List<String> allowedHosts) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }
        if (!SCHEME_PATTERN.matcher(url).find()) {
            throw new IllegalArgumentException("URL must use http or https scheme: " + url);
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL syntax: " + url, e);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL has no host: " + url);
        }
        // 1. Hostname allowlist
        boolean hostAllowed = false;
        for (String allowed : allowedHosts != null ? allowedHosts : DEFAULT_ALLOWED_HOSTS) {
            if (host.equalsIgnoreCase(allowed) || host.toLowerCase().endsWith("." + allowed.toLowerCase())) {
                hostAllowed = true;
                break;
            }
        }
        if (!hostAllowed) {
            log.warn("SSRF protection: rejected URL — host not in allow list: {}", host);
            throw new IllegalArgumentException("Host not in allow list: " + host);
        }
        // 2. IP address block — only if host is an IP literal
        try {
            InetAddress addr = InetAddress.getByName(host);
            String ip = addr.getHostAddress();
            for (String prefix : BLOCKED_IP_PREFIXES) {
                if (ip.startsWith(prefix)) {
                    log.warn("SSRF protection: rejected URL — resolves to private/reserved IP {}: {}",
                            ip, url);
                    throw new IllegalArgumentException(
                            "Host resolves to a private/reserved IP range: " + ip);
                }
            }
        } catch (UnknownHostException e) {
            // Host not resolvable — let the actual HTTP client fail later
            log.debug("Could not resolve host during SSRF check: {} (will retry at call time)", host);
        }
    }

    /**
     * Convenience overload using default allowed hosts.
     */
    public static void validate(String url) {
        validate(url, DEFAULT_ALLOWED_HOSTS);
    }

    /**
     * Return the set of default allowed hosts (for the admin UI to display).
     */
    public static Set<String> defaultAllowedHosts() {
        return new HashSet<>(DEFAULT_ALLOWED_HOSTS);
    }
}
