package com.selfcare.config.compiler;

import com.selfcare.config.compiler.ConfigCompiler.CompiledManifest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Verifies the HMAC signature of an immutable experience manifest (constant-time comparison).
 */
@Component
public class ManifestVerifier {

    private final ManifestSigner signer;

    public ManifestVerifier(ManifestSigner signer) {
        this.signer = signer;
    }

    public boolean verify(CompiledManifest manifest) {
        if (manifest.getSignature() == null || manifest.getManifestId() == null) {
            return false;
        }
        String expected = signer.sign(ManifestSigner.canonical(manifest));
        return constantTimeEquals(expected, manifest.getSignature());
    }

    private boolean constantTimeEquals(String a, String b) {
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}