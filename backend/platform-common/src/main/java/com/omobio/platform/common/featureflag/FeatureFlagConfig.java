package com.omobio.platform.common.featureflag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feature flag configuration — wires up the default feature flag client.
 *
 * If Unleash is on the classpath and configured, UnleashFeatureFlagClient is
 * used. Otherwise, the NoOpFeatureFlagClient is the default.
 */
@Slf4j
@Configuration
public class FeatureFlagConfig {

    @Bean
    @ConditionalOnMissingBean(FeatureFlagClient.class)
    public FeatureFlagClient defaultFeatureFlagClient() {
        log.info("Using NoOpFeatureFlagClient (default)");
        return new NoOpFeatureFlagClient();
    }
}
