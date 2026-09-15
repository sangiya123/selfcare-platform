package com.selfcare.platform.common.security;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
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
     * via configuration — extend with the
     * {@code SELFCARE_SECURITY_URL_ALLOWLIST_HOSTS} environment variable
     * (comma-separated host suffixes; hosts listed there are trusted, so
     * mock/private endpoints and in-cluster {@code *.svc} names keep working).
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

    /**
     * Explicitly configured extra hosts (mock/private endpoints an operator
     * chooses to trust). Populated from {@link #configure(List)} and/or the
     * {@code SELFCARE_SECURITY_URL_ALLOWLIST_HOSTS} environment variable /
     * {@code selfcare.security.url-allowlist.hosts} system property
     * (comma-separated host suffixes).
     */
    private static volatile List<String> extraHosts = List.of();

    private UrlAllowlist() {}

    /**
     * Explicitly extend the allow list with operator-chosen mock/private hosts.
     *
     * <p>Programmatic equivalent of the {@code SELFCARE_SECURITY_URL_ALLOWLIST_HOSTS}
     * environment variable. Hosts added here bypass the private/reserved-IP
     * rejection ({@code localhost}, in-cluster {@code *.svc} names, ...) so
     * local mock and live-cluster flows keep working. Pass {@code null} or an
     * empty list to clear.</p>
     */
    public static synchronized void configure(List<String> hosts) {
        extraHosts = hosts != null ? List.copyOf(hosts) : List.of();
    }

    /** Clear any hosts added via {@link #configure(List)}. */
    public static synchronized void reset() {
        extraHosts = List.of();
    }

    /**
     * Merge of the default and explicitly configured allow lists.
     */
    public static List<String> effectiveAllowedHosts() {
        List<String> hosts = new ArrayList<>(DEFAULT_ALLOWED_HOSTS);
        hosts.addAll(effectiveExtraHosts());
        return hosts;
    }

    private static List<String> effectiveExtraHosts() {
        String env = System.getProperty("selfcare.security.url-allowlist.hosts");
        if (env == null || env.isBlank()) {
            env = System.getenv("SELFCARE_SECURITY_URL_ALLOWLIST_HOSTS");
        }
        if (env == null || env.isBlank()) {
            return extraHosts;
        }
        List<String> merged = new ArrayList<>(extraHosts);
        for (String h : env.split(",")) {
            String t = h.trim();
            if (!t.isBlank()) {
                merged.add(t);
            }
        }
        return merged;
    }

    /**
     * True when the host matches one of the baked-in application defaults
     * (operator BSS / AI provider hosts). Default hosts always keep the
     * private-IP check; explicitly configured hosts are trusted by the operator.
     */
    private static boolean isDefaultHost(String host) {
        String lower = host.toLowerCase();
        for (String allowed : DEFAULT_ALLOWED_HOSTS) {
            if (lower.equals(allowed) || lower.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validate that a URL is safe to call. Throws if the URL fails any check.
     *
     * @param url the URL to validate
     * @param allowedHosts the list of allowed hostnames (suffix match)
     * @param trustedConfiguredHosts when true, hosts that match the explicitly
     *                               configured allow list (not the baked-in
     *                               defaults) bypass the private-IP rejection
     * @throws IllegalArgumentException if the URL is not allowed
     */
    public static void validate(String url, List<String> allowedHosts, boolean trustedConfiguredHosts) {
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
        // 2. IP address block — always enforced for the baked-in default hosts.
        //    Explicitly configured (trusted) mock/private hosts skip this layer.
        if (!trustedConfiguredHosts || isDefaultHost(host)) {
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
    }

    /**
     * Validate that a URL is safe to call. Throws if the URL fails any check.
     *
     * @param url the URL to validate
     * @param allowedHosts the list of allowed hostnames (suffix match)
     * @throws IllegalArgumentException if the URL is not allowed
     */
    public static void validate(String url, List<String> allowedHosts) {
        validate(url, allowedHosts, false);
    }

    /**
     * Convenience overload using the effective allow list (defaults +
     * explicitly configured hosts). Private/reserved-IP hosts are rejected
     * unless they appear in the explicitly configured allow list.
     */
    public static void validate(String url) {
        validate(url, effectiveAllowedHosts(), true);
    }

    /**
     * Return the set of default allowed hosts (for the admin UI to display).
     */
    public static Set<String> defaultAllowedHosts() {
        return new HashSet<>(DEFAULT_ALLOWED_HOSTS);
    }
}
