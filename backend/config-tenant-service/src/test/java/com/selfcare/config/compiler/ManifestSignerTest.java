package com.selfcare.config.compiler;

import com.selfcare.config.compiler.ConfigCompiler.CompiledManifest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestSignerTest {

    private final ManifestSigner signer = new ManifestSigner("unit-test-key");
    private final ManifestVerifier verifier = new ManifestVerifier(signer);

    private CompiledManifest manifest() {
        CompiledManifest m = new CompiledManifest();
        m.setManifestId("abc123");
        m.setTenant("dialog-lk");
        m.setEnvironment("prod");
        m.setExperience("home");
        m.setProfileKey("mobile_prepaid");
        m.setConfigVersion(17);
        m.setSigningAlg(ManifestSigner.ALGORITHM);
        m.setSignature(signer.sign(m));
        return m;
    }

    @Test
    void signedManifestVerifies() {
        assertTrue(verifier.verify(manifest()));
    }

    @Test
    void tamperedContentFailsVerification() {
        CompiledManifest m = manifest();
        m.setConfigVersion(18);
        assertFalse(verifier.verify(m));
    }

    @Test
    void signatureIsDeterministicForSameInput() {
        ManifestSigner other = new ManifestSigner("unit-test-key");
        assertTrue(other.sign(manifest()).equals(signer.sign(manifest())));
    }

    @Test
    void differentKeyProducesDifferentSignature() {
        ManifestSigner other = new ManifestSigner("other-key");
        assertNotEquals(manifest().getSignature(), other.sign(manifest()));
    }
}