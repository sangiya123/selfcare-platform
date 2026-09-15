package com.selfcare.platform.common.featureflag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op feature flag client used when Unleash is not configured.
 *
 * All flags are disabled by default. This ensures safe behavior in
 * environments without a feature flag service.
 *
 * Configuration:
 *   selfcare.featureflags.enabled=false  (default)
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "selfcare.featureflags", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpFeatureFlagClient implements FeatureFlagClient {

    @Override
    public FeatureFlagResult isEnabled(String flagName, String tenantId) {
        log.trace("NoOp feature flag check: {} for tenant {}", flagName, tenantId);
        return FeatureFlagResult.disabled();
    }

    @Override
    public FeatureFlagResult getVariant(String flagName, String tenantId) {
        log.trace("NoOp feature flag variant: {} for tenant {}", flagName, tenantId);
        return FeatureFlagResult.disabled();
    }

    @Override
    public FeatureFlagResult isEnabled(String flagName, String tenantId, String key, Object value) {
        return FeatureFlagResult.disabled();
    }

    @Override
    public FeatureFlagResult getVariant(String flagName, String tenantId, String key, Object value) {
        return FeatureFlagResult.disabled();
    }

    @Override
    public FeatureFlagResult isEnabled(String flagName, String tenantId, java.util.Map<String, Object> contextAttributes) {
        return FeatureFlagResult.disabled();
    }

    @Override
    public FeatureFlagResult getVariant(String flagName, String tenantId, java.util.Map<String, Object> contextAttributes) {
        return FeatureFlagResult.disabled();
    }
}