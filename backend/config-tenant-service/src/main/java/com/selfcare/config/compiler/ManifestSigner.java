package com.selfcare.config.compiler;

import com.selfcare.config.compiler.ConfigCompiler.CompiledManifest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HMAC-SHA256 signer for immutable experience manifests.
 *
 * The signature binds {manifestId, tenant, environment, experience, profileKey, configVersion}
 * so a signed manifest cannot be edited or reassigned without the key. Consuming apps/services
 * verify with {@link ManifestVerifier}.
 *
 * Key resolution: {@code selfcare.manifest.signing-key} (env/property/secret). A dev-only fallback
 * keeps local clusters functional; production must set the key via secret.
 */
@Slf4j
@Component
public class ManifestSigner {

    public static final String ALGORITHM = "HMAC-SHA256";
    public static final String DEV_FALLBACK_KEY = "selfcare-dev-unsafe-signing-key-0000";

    private final String signingKey;

    public ManifestSigner(@Value("${selfcare.manifest.signing-key:" + DEV_FALLBACK_KEY + "}") String signingKey) {
        this.signingKey = signingKey;
        if (DEV_FALLBACK_KEY.equals(signingKey)) {
            log.warn("Manifest signing uses the DEV fallback key — set selfcare.manifest.signing-key in production");
        }
    }

    public String sign(CompiledManifest manifest) {
        return sign(canonical(manifest));
    }

    public String sign(String canonicalInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(canonicalInput.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Signing failed", e);
        }
    }

    /** Stable, canonical signing input independent of field ordering. */
    public static String canonical(CompiledManifest m) {
        return m.getManifestId() + "|"
                + m.getTenant() + "|"
                + m.getEnvironment() + "|"
                + m.getExperience() + "|"
                + m.getProfileKey() + "|"
                + m.getConfigVersion();
    }
}