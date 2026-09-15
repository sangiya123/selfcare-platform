package com.selfcare.platform.common.security;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * CryptoUtils — common cryptographic helpers used across services.
 *
 * Wraps the JDK crypto APIs and provides:
 *   - SHA-256 hashing (for anonymization, e.g. GDPR erasure record)
 *   - HMAC-SHA-256 (for webhook signature verification)
 *   - Secure random bytes
 *   - Constant-time equality
 *   - PIN / OTP generation
 *   - Numeric / alphabet token generation
 *
 * All methods are deterministic where possible. For key derivation
 * (HKDF, PBKDF2, scrypt) use a dedicated KDF — this class is for
 * simple primitives.
 */
@Slf4j
public final class CryptoUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtils() {}

    /**
     * Compute SHA-256 of the input, returning lowercase hex.
     */
    public static String sha256(String input) {
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Compute SHA-256 of the byte array, returning lowercase hex.
     */
    public static String sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Compute HMAC-SHA-256 of (key, message), returning lowercase hex.
     * Used for webhook signature verification.
     */
    public static String hmacSha256(String key, String message) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec =
                    new javax.crypto.spec.SecretKeySpec(
                            key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] result = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA-256 failed", e);
        }
    }

    /**
     * Generate a cryptographically random byte array of the given length.
     */
    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * Generate a cryptographically random string from the default
     * alphabet (digits + lowercase + uppercase), of the given length.
     */
    public static String randomString(int length) {
        return randomString(length, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
    }

    /**
     * Generate a cryptographically random string from a custom alphabet.
     */
    public static String randomString(int length, String alphabet) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /**
     * Generate a numeric OTP of the given length.
     */
    public static String generateOtp(int length) {
        return randomString(length, "0123456789");
    }

    /**
     * Generate a UUIDv4-style random identifier.
     */
    public static String generateId() {
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * Constant-time string comparison. Use when comparing MAC, HMAC, or
     * any value where timing leakage matters. Returns true if both
     * strings are equal AND have the same length.
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    /**
     * Constant-time byte array comparison.
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    /**
     * Hash a user identifier (e.g. MSISDN, NIC, email) with a tenant salt
     * for anonymized storage. The same input under the same tenant always
     * produces the same hash, allowing deduplication.
     */
    public static String hashUserId(String tenantId, String userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(tenantId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(userId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Mask a string showing only the last N characters.
     * Used in logs and UI to avoid showing full PII.
     */
    public static String mask(String value, int visibleSuffix) {
        if (value == null || value.isEmpty()) return value;
        if (visibleSuffix < 0) throw new IllegalArgumentException("visibleSuffix must not be negative");
        if (value.length() <= visibleSuffix || value.length() - visibleSuffix < 2) return value;
        return "*".repeat(value.length() - visibleSuffix) + value.substring(value.length() - visibleSuffix);
    }

    /**
     * Mask an email address: keep first char + @ + domain.
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }

    /**
     * Mask a phone number: keep country code and last 2 digits.
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        String digits = phone.startsWith("+") ? phone.substring(1) : phone;
        if (digits.length() < 4) return "***";
        return "+" + digits.substring(0, 3) + "****" + digits.substring(digits.length() - 2);
    }
}
