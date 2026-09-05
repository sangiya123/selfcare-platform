package com.omobio.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * WAF (Web Application Firewall) Gateway Filter.
 *
 * Enforces OWASP-aligned security rules on every request:
 *   - SQL injection detection
 *   - XSS detection
 *   - Path traversal
 *   - Command injection
 *   - SSRF (private/reserved IP ranges)
 *   - HTTP method allow list
 *   - Rate limiting (per-IP and per-route)
 *   - Body size limits
 *
 * Rules are loaded from {@code classpath:waf-rules.yml}. When
 * {@code omobio.waf.enabled=false} (default), this filter is a no-op.
 *
 * Usage in application.yml:
 * <pre>
 * filters:
 *   - name: Waf
 * </pre>
 */
@Slf4j
@Component
public class WafGatewayFilterFactory
        extends AbstractGatewayFilterFactory<WafGatewayFilterFactory.Config> {

    private final List<WafRule> rules = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, AtomicInteger> ipRateCounters = new HashMap<>();
    private final Map<String, Map<String, AtomicInteger>> routeRateCounters = new HashMap<>();
    private Instant lastCounterReset = Instant.now();

    @Value("${omobio.waf.enabled:false}")
    private boolean enabled;

    @Value("${omobio.waf.rules-file:classpath:waf-rules.yml}")
    private String rulesResource;

    @Value("${omobio.waf.max-body-size:1048576}")
    private long maxBodySize;

    public WafGatewayFilterFactory() {
        super(Config.class);
    }

    @jakarta.annotation.PostConstruct
    public void loadRules() {
        if (!enabled) {
            log.info("WAF filter is disabled");
            return;
        }
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource(rulesResource);
            if (!resource.exists()) {
                log.warn("WAF rules file not found: {}", rulesResource);
                return;
            }
            try (InputStream in = resource.getInputStream()) {
                WafRulesYaml config = objectMapper.readValue(in, WafRulesYaml.class);
                if (config.rules != null) {
                    for (WafRuleYaml r : config.rules) {
                        rules.add(toRule(r));
                    }
                }
                log.info("WAF loaded {} rules from {}", rules.size(), rulesResource);
            }
        } catch (IOException e) {
            log.error("Failed to load WAF rules from {}", rulesResource, e);
        }
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if (!enabled) {
                return chain.filter(exchange);
            }

            ServerHttpRequest request = exchange.getRequest();
            String clientIp = extractClientIp(request);
            String path = request.getPath().value();
            String method = request.getMethod().name();

            // Periodic counter reset (every 60 seconds)
            if (Duration.between(lastCounterReset, Instant.now()).getSeconds() > 60) {
                ipRateCounters.clear();
                routeRateCounters.clear();
                lastCounterReset = Instant.now();
            }

            // ---- 1. HTTP method allow list ----
            List<String> allowedMethods = Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD");
            if (!allowedMethods.contains(method)) {
                return reject(exchange, "HTTP method not allowed: " + method);
            }

            // ---- 2. Per-IP rate limit ----
            if (isRateLimited(clientIp, "global", 300)) {
                return reject(exchange, "Rate limit exceeded for IP " + clientIp);
            }

            // ---- 3. Auth endpoint rate limit (stricter) ----
            if ((path.contains("/auth/login") || path.contains("/auth/otp"))
                    && isRouteRateLimited(path, clientIp, 10)) {
                return reject(exchange, "Too many authentication attempts");
            }

            // ---- 4. SQL injection / XSS / path traversal / command injection detection ----
            String fullUrl = path + "?" + (request.getURI().getQuery() != null ? request.getURI().getQuery() : "");
            for (WafRule rule : rules) {
                if (rule.pattern == null) continue;
                if (rule.action == WafAction.SECURITY_HEADERS) continue; // handled later
                if (matchesPath(path, rule.appliesToPaths) && rule.pattern.matcher(fullUrl).find()) {
                    if (rule.action == WafAction.BLOCK) {
                        log.warn("WAF blocked: rule={} ip={} url={}", rule.id, clientIp, fullUrl);
                        return reject(exchange, rule.message);
                    } else if (rule.action == WafAction.LOG) {
                        log.info("WAF log: rule={} ip={} url={}", rule.id, clientIp, fullUrl);
                    }
                }
            }

            // ---- 5. SSRF: detect private IP in request URL ----
            if (containsPrivateIp(fullUrl)) {
                return reject(exchange, "Request URL contains a private/reserved IP");
            }

            // ---- 6. Body size ----
            Long contentLength = request.getHeaders().getContentLength();
            if (contentLength != null && contentLength > maxBodySize) {
                return reject(exchange, "Request body exceeds " + maxBodySize + " bytes");
            }

            // ---- 7. Mutate request with security headers ----
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                ServerHttpResponse response = exchange.getResponse();
                response.getHeaders().add("X-Frame-Options", "DENY");
                response.getHeaders().add("X-Content-Type-Options", "nosniff");
                response.getHeaders().add("X-XSS-Protection", "1; mode=block");
                response.getHeaders().add("Strict-Transport-Security",
                        "max-age=31536000; includeSubDomains");
                response.getHeaders().add("Content-Security-Policy", "default-src 'self'");
                response.getHeaders().add("Referrer-Policy", "strict-origin-when-cross-origin");
            }));
        };
    }

    private Mono<Void> reject(org.springframework.web.server.ServerWebExchange exchange,
                              String reason) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            String body = "{\"error\":{\"code\":\"WAF_BLOCKED\","
                    + "\"message\":\"" + reason.replace("\"", "'") + "\"}}";
            return response.writeWith(Mono.just(response.bufferFactory()
                    .wrap(body.getBytes())));
        } catch (Exception e) {
            return response.setComplete();
        }
    }

    private boolean isRateLimited(String clientIp, String scope, int max) {
        AtomicInteger counter = ipRateCounters.computeIfAbsent(
                clientIp + ":" + scope, k -> new AtomicInteger(0));
        return counter.incrementAndGet() > max;
    }

    private boolean isRouteRateLimited(String path, String clientIp, int max) {
        Map<String, AtomicInteger> counters = routeRateCounters
                .computeIfAbsent(path, k -> new HashMap<>());
        AtomicInteger counter = counters.computeIfAbsent(
                clientIp, k -> new AtomicInteger(0));
        return counter.incrementAndGet() > max;
    }

    private boolean matchesPath(String path, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return true;
        for (String p : patterns) {
            String regex = p.replace("**", ".*").replace("*", "[^/]*");
            if (Pattern.compile(regex).matcher(path).matches()) return true;
        }
        return false;
    }

    private boolean containsPrivateIp(String url) {
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null) return false;
            // Check if host is an IP address
            try {
                InetAddress addr = InetAddress.getByName(uri.getHost());
                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                        || addr.isAnyLocalAddress() || addr.isMulticastAddress()
                        || addr.isSiteLocalAddress()) {
                    return true;
                }
                byte[] bytes = addr.getAddress();
                // 169.254.x.x (AWS metadata)
                if (bytes.length == 4 && (bytes[0] & 0xFF) == 169
                        && (bytes[1] & 0xFF) == 254) {
                    return true;
                }
            } catch (UnknownHostException ignored) {
                return false;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String extractClientIp(ServerHttpRequest request) {
        String xFwd = request.getHeaders().getFirst("X-Forwarded-For");
        if (xFwd != null && !xFwd.isBlank()) {
            return xFwd.split(",")[0].trim();
        }
        if (request.getRemoteAddress() != null
                && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    private WafRule toRule(WafRuleYaml yaml) {
        WafRule rule = new WafRule();
        rule.id = yaml.id;
        rule.message = yaml.message;
        rule.appliesToPaths = yaml.appliesToPaths;
        if (yaml.pattern != null && !yaml.pattern.isBlank()) {
            rule.pattern = Pattern.compile(yaml.pattern, Pattern.CASE_INSENSITIVE);
        }
        rule.action = switch (yaml.action != null ? yaml.action : "log") {
            case "block" -> WafAction.BLOCK;
            case "log" -> WafAction.LOG;
            case "add" -> WafAction.SECURITY_HEADERS;
            default -> WafAction.LOG;
        };
        return rule;
    }

    public static class Config {
    }

    private static class WafRule {
        long id;
        String message;
        List<String> appliesToPaths;
        Pattern pattern;
        WafAction action;
    }

    private enum WafAction {
        BLOCK, LOG, SECURITY_HEADERS
    }

    // YAML DTOs (Jackson-mapped)
    public static class WafRulesYaml {
        public List<WafRuleYaml> rules;
    }

    public static class WafRuleYaml {
        public long id;
        public String phase;
        public String severity;
        public String action;
        public String pattern;
        public String message;
        public List<String> appliesToPaths;
        public Long maxBodySize;
        public RateLimit rateLimit;
        public Map<String, String> headersToAdd;
    }

    public static class RateLimit {
        public int windowSeconds;
        public int maxRequests;
    }
}
