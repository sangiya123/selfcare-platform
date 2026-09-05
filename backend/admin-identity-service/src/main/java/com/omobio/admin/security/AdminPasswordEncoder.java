package com.omobio.admin.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bcrypt password encoder for admin users.
 *
 * Uses a configurable strength (default: 12, sufficient for production).
 * Always pass plain-text passwords; never log the result.
 */
@Slf4j
@Component
public class AdminPasswordEncoder {

    private final PasswordEncoder delegate;

    public AdminPasswordEncoder(@Value("${omobio.security.password.bcrypt-strength:12}") int strength) {
        this.delegate = new BCryptPasswordEncoder(strength);
        log.info("Admin password encoder initialized: bcrypt strength={}", strength);
    }

    /**
     * Hash a plain-text password.
     *
     * @param plainPassword the password to hash
     * @return the bcrypt hash (never log this)
     */
    public String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        return delegate.encode(plainPassword);
    }

    /**
     * Verify a plain-text password against a stored hash.
     *
     * @param plainPassword the candidate password
     * @param hashedPassword the stored hash
     * @return true if matches
     */
    public boolean matches(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        return delegate.matches(plainPassword, hashedPassword);
    }

    /**
     * Check whether a hash needs upgrading (e.g. strength changed).
     */
    public boolean needsUpgrade(String hashedPassword) {
        if (!(delegate instanceof BCryptPasswordEncoder bcrypt)) {
            return false;
        }
        return bcrypt.upgradeEncoding(hashedPassword);
    }
}
