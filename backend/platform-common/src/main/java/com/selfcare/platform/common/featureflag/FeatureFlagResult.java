package com.selfcare.platform.common.featureflag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a feature flag evaluation.
 *
 * Carries:
 *   - enabled: whether the flag is on for this tenant/context
 *   - variant: the variant value when the flag has variants (A/B/n)
 *   - error: any evaluation error (provider unreachable, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlagResult {

    private boolean enabled;
    private String variant;
    private String error;
    private String reason;

    public static FeatureFlagResult enabled() {
        return FeatureFlagResult.builder().enabled(true).build();
    }

    public static FeatureFlagResult enabled(String variant) {
        return FeatureFlagResult.builder().enabled(true).variant(variant).build();
    }

    public static FeatureFlagResult disabled() {
        return FeatureFlagResult.builder().enabled(false).build();
    }

    public static FeatureFlagResult error(String error) {
        return FeatureFlagResult.builder()
                .enabled(false)
                .error(error)
                .reason("evaluation_error")
                .build();
    }
}
