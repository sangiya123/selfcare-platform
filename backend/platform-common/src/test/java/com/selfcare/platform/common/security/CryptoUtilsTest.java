package com.selfcare.platform.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CryptoUtilsTest {

    @Test
    @DisplayName("sha256 produces 64 hex chars for known vector")
    void sha256_knownVector() {
        // "hello world" SHA-256
        String h = CryptoUtils.sha256("hello world");
        assertThat(h).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    }

    @Test
    @DisplayName("sha256 is deterministic")
    void sha256_deterministic() {
        assertThat(CryptoUtils.sha256("test"))
            .isEqualTo(CryptoUtils.sha256("test"));
    }

    @Test
    @DisplayName("hmacSha256 is deterministic for same inputs")
    void hmac_deterministic() {
        String h1 = CryptoUtils.hmacSha256("secret", "payload");
        String h2 = CryptoUtils.hmacSha256("secret", "payload");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("hmacSha256 changes when key or message changes")
    void hmac_changesWithKey() {
        String h1 = CryptoUtils.hmacSha256("secret1", "payload");
        String h2 = CryptoUtils.hmacSha256("secret2", "payload");
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("randomBytes produces requested length")
    void randomBytes_length() {
        byte[] bytes = CryptoUtils.randomBytes(32);
        assertThat(bytes).hasSize(32);
    }

    @Test
    @DisplayName("randomString produces unique strings")
    void randomString_unique() {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) {
            set.add(CryptoUtils.randomString(32));
        }
        assertThat(set).hasSize(100);
    }

    @Test
    @DisplayName("generateOtp is digits only")
    void generateOtp_digitsOnly() {
        String otp = CryptoUtils.generateOtp(6);
        assertThat(otp).hasSize(6).matches("\\d{6}");
    }

    @Test
    @DisplayName("constantTimeEquals true for equal")
    void constantTimeEqual_true() {
        assertThat(CryptoUtils.constantTimeEquals("abc", "abc")).isTrue();
    }

    @Test
    @DisplayName("constantTimeEquals false for different")
    void constantTimeEqual_false() {
        assertThat(CryptoUtils.constantTimeEquals("abc", "abd")).isFalse();
        assertThat(CryptoUtils.constantTimeEquals("abc", "abcd")).isFalse();
    }

    @Test
    @DisplayName("constantTimeEquals handles nulls")
    void constantTimeEqual_null() {
        assertThat(CryptoUtils.constantTimeEquals((String) null, "abc")).isFalse();
        assertThat(CryptoUtils.constantTimeEquals("abc", (String) null)).isFalse();
        assertThat(CryptoUtils.constantTimeEquals((String) null, (String) null)).isFalse();
    }

    @Test
    @DisplayName("hashUserId is deterministic for same tenant + user")
    void hashUserId_deterministic() {
        String h1 = CryptoUtils.hashUserId("dialog-lk", "94771123456");
        String h2 = CryptoUtils.hashUserId("dialog-lk", "94771123456");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("hashUserId differs across tenants")
    void hashUserId_tenantIsolation() {
        String a = CryptoUtils.hashUserId("dialog-lk", "94771123456");
        String b = CryptoUtils.hashUserId("hutch-lk", "94771123456");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("mask keeps suffix only")
    void mask() {
        assertThat(CryptoUtils.mask("1234567890", 4)).isEqualTo("******7890");
        assertThat(CryptoUtils.mask("short", 4)).isEqualTo("short");
    }

    @Test
    @DisplayName("maskEmail")
    void maskEmail() {
        assertThat(CryptoUtils.maskEmail("john.doe@example.com")).isEqualTo("j***@example.com");
        assertThat(CryptoUtils.maskEmail(null)).isEqualTo("***");
        assertThat(CryptoUtils.maskEmail("invalid")).isEqualTo("***");
    }

    @Test
    @DisplayName("maskPhone keeps prefix and last 2 digits")
    void maskPhone() {
        assertThat(CryptoUtils.maskPhone("+94771123456"))
            .isEqualTo("+947****56");
        assertThat(CryptoUtils.maskPhone("123")).isEqualTo("***");
    }
}
