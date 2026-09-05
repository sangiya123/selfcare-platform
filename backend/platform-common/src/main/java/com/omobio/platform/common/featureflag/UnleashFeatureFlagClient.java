package com.omobio.platform.common.featureflag;

import com.omobio.platform.common.tenant.TenantContext;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.getunleash.variant.Variant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Feature flag client backed by Unleash.
 *
 * <p>Activated when {@code omobio.featureflags.provider=unleash} AND an
 * {@code io.getunleash.Unleash} bean is present in the application context.
 * When the provider is not configured, {@link NoOpFeatureFlagClient} is the
 * default — see {@link com.omobio.platform.common.featureflag.FeatureFlagConfig}.</p>
 *
 * <p>Provides tenant-aware feature flag evaluation with context:
 * <ul>
 *   <li>tenantId</li>
 *   <li>environment (dev/qa/staging/reg/prod)</li>
 *   <li>userId (if authenticated)</li>
 *   <li>custom attributes (LOB, connection type, segment, etc.)</li>
 * </ul>
 *
 * <p>Configuration:
 * <pre>
 * omobio.featureflags.provider=unleash
 * omobio.featureflags.unleash.url=https://unleash.example.com/api/
 * omobio.featureflags.unleash.token=UNLEASH_TOKEN
 * omobio.featureflags.unleash.appName=omobio-selfcare
 * </pre>
 */
@Slf4j
@Component
@Primary
@ConditionalOnBean(Unleash.class)
@RequiredArgsConstructor
public class UnleashFeatureFlagClient implements FeatureFlagClient {

    private final ObjectProvider<Unleash> unleashProvider;

    @Override
    public FeatureFlagResult isEnabled(String flagName, String tenantId) {
        return evaluate(flagName, tenantId, null);
    }

    @Override
    public FeatureFlagResult getVariant(String flagName, String tenantId) {
        return evaluateVariant(flagName, tenantId, null);
    }

    @Override
    public FeatureFlagResult isEnabled(String flagName, String tenantId, String key, Object value) {
        return evaluate(flagName, tenantId, Map.of(key, value));
    }

    @Override
    public FeatureFlagResult getVariant(String flagName, String tenantId, String key, Object value) {
        return evaluateVariant(flagName, tenantId, Map.of(key, value));
    }

    @Override
    public FeatureFlagResult isEnabled(String flagName, String tenantId, Map<String, Object> contextAttributes) {
        return evaluate(flagName, tenantId, contextAttributes);
    }

    @Override
    public FeatureFlagResult getVariant(String flagName, String tenantId, Map<String, Object> contextAttributes) {
        return evaluateVariant(flagName, tenantId, contextAttributes);
    }

    private FeatureFlagResult evaluate(String flagName, String tenantId, Map<String, Object> extras) {
        try {
            Unleash u = current();
            if (u == null) return FeatureFlagResult.disabled();
            UnleashContext ctx = buildContext(tenantId, extras);
            boolean enabled = u.isEnabled(flagName, ctx, false);
            return enabled ? FeatureFlagResult.enabled() : FeatureFlagResult.disabled();
        } catch (Exception e) {
            log.warn("Unleash evaluation failed for flag {} tenant {}: {}", flagName, tenantId, e.getMessage());
            return FeatureFlagResult.error(e.getMessage());
        }
    }

    private FeatureFlagResult evaluateVariant(String flagName, String tenantId, Map<String, Object> extras) {
        try {
            Unleash u = current();
            if (u == null) return FeatureFlagResult.disabled();
            UnleashContext ctx = buildContext(tenantId, extras);
            Variant variant = u.getVariant(flagName, ctx, null);
            if (variant == null || variant.getName() == null || variant.getName().isBlank()) {
                return FeatureFlagResult.disabled();
            }
            return FeatureFlagResult.enabled(variant.getName());
        } catch (Exception e) {
            log.warn("Unleash variant evaluation failed for flag {} tenant {}: {}", flagName, tenantId, e.getMessage());
            return FeatureFlagResult.error(e.getMessage());
        }
    }

    private Unleash current() {
        return unleashProvider.getIfAvailable();
    }

    private UnleashContext buildContext(String tenantId, Map<String, Object> extras) {
        UnleashContext.Builder builder = UnleashContext.builder()
            .appName("omobio-selfcare")
            .addProperty("tenantId", tenantId == null ? "unknown" : tenantId);

        try {
            TenantContext tc = TenantContext.get();
            String env = tc.getEnvironment();
            if (env != null) builder.addProperty("environment", env);
            String userId = tc.getUserId();
            if (userId != null) builder.addProperty("userId", userId);
            String sessionId = tc.getSessionId();
            if (sessionId != null) builder.addProperty("sessionId", sessionId);
        } catch (Exception ignored) {
            // No tenant context (e.g., background job) — fine
        }

        if (extras != null) {
            extras.forEach((k, v) -> {
                if (v != null) {
                    builder.addProperty(k, String.valueOf(v));
                }
            });
        }
        return builder.build();
    }
}
