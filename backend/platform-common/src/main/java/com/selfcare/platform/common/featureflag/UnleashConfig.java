package com.selfcare.platform.common.featureflag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Unleash feature flag client configuration.
 *
 * Activated when {@code selfcare.featureflags.provider=unleash}.
 * Requires the {@code com.github.valfirst:unleash-spring-boot-starter} dependency.
 *
 * When not activated (default), {@link NoOpFeatureFlagClient} is used.
 *
 * Configuration:
 *   selfcare.featureflags.provider=unleash
 *   selfcare.featureflags.unleash.url=https://unleash.example.com/api/
 *   selfcare.featureflags.unleash.token=<your-api-token>
 *   selfcare.featureflags.unleash.appName=selfcare
 *
 * @see NoOpFeatureFlagClient
 * @see FeatureFlagClient
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "selfcare.featureflags", name = "provider", havingValue = "unleash")
public class UnleashConfig {

    @Value("${selfcare.featureflags.unleash.url:https://unleash.example.com/api/}")
    private String unleashUrl;

    @Value("${selfcare.featureflags.unleash.token:}")
    private String unleashToken;

    @Value("${selfcare.featureflags.unleash.appName:selfcare}")
    private String appName;

    /**
     * Creates an Unleash client bean.
     *
     * <p>To enable, add to your service's pom.xml:
     * <pre>
     * &lt;dependency&gt;
     *     &lt;groupId&gt;com.github.valfirst&lt;/groupId&gt;
     *     &lt;artifactId&gt;unleash-spring-boot-starter&lt;/artifactId&gt;
     *     &lt;version&gt;2.5.0&lt;/version&gt;
     * &lt;/dependency&gt;
     * </pre>
     *
     * <p>Or use the native Java client directly:
     * <pre>
     * DefaultUnleash unleash = new DefaultUnleash(UnleashConfig.builder()
     *     .appName(appName)
     *     .unleashAPI(unleashUrl)
     *     .apiKey(unleashToken)
     *     .build());
     * </pre>
     */
    @Bean
    public FeatureFlagClient unleashFeatureFlagClient() {
        log.warn(
            "Unleash provider activated but io.getunleash:unleash-client-java is not available in platform-common. " +
            "Add the dependency to enable Unleash: " +
            "https://github.com/Unleash/unleash-client-java. " +
            "Falling back to NoOpFeatureFlagClient."
        );
        return new NoOpFeatureFlagClient();
    }
}
