package com.selfcare.admin.service;

import com.selfcare.admin.domain.AdminMfaBackupCode;
import com.selfcare.admin.domain.AdminUser;
import com.selfcare.admin.repository.AdminMfaBackupCodeRepository;
import com.selfcare.admin.repository.AdminUserRepository;
import com.selfcare.platform.common.web.BadRequestException;
import com.selfcare.platform.common.web.NotFoundException;
import com.selfcare.platform.common.web.UnauthorizedException;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * TOTP-based MFA service for admin users (Google Authenticator / Authy).
 *
 * Implements the three-step setup flow:
 * <ol>
 *   <li>{@code /setup} — generates a new secret, encrypts it, stores it on
 *       the {@link AdminUser}, and returns a provisioning URI + QR code.</li>
 *   <li>{@code /verify-setup} — verifies the TOTP code from the app, then
 *       commits {@code mfa_enabled = true} and generates backup codes.</li>
 *   <li>{@code /verify} — validates the TOTP code on every login when MFA is
 *       enforced.</li>
 * </ol>
 *
 * The TOTP secret is stored AES-256-GCM encrypted in {@code mfa_secret}.
 * Backup codes are hashed with SHA-256 before storage.
 *
 * @see <a href="https://github.com/samstevens/totp">dev.samstevens.totp</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

    private static final String MFA_TYPE_TOTP = "TOTP";
    private static final int BACKUP_CODE_COUNT = 10;
    private static final int BACKUP_CODE_LENGTH = 10;

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";

    private final AdminUserRepository userRepository;
    private final AdminMfaBackupCodeRepository backupCodeRepository;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${selfcare.mfa.issuer:selfcare}")
    private String issuer;

    /**
     * Begin MFA setup: generates a TOTP secret, encrypts it, stores it
     * temporarily on the user, and returns the provisioning URI + base64 QR code.
     *
     * The secret is NOT yet active — it only becomes active after
     * {@link #completeSetup(String, String)} is called.
     *
     * @param userId admin user ID
     * @return setup payload: provisioning URI, QR code (base64 PNG), and
     *         a temporary setup token for the next step
     */
    @Transactional
    public SetupResult beginSetup(String userId) {
        AdminUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        String rawSecret = secretGenerator.generate();
        String provisioningUri = buildProvisioningUri(rawSecret, user.getEmail());
        String encryptedSecret = encryptSecret(rawSecret);

        user.setMfaType(MFA_TYPE_TOTP);
        user.setMfaSecret(encryptedSecret);
        // mfaEnabled stays false until verify-setup confirms possession
        userRepository.save(user);

        String setupToken = UUID.randomUUID().toString();

        log.info("MFA setup initiated for userId={}", userId);
        return new SetupResult(
                provisioningUri,
                generateQrCodeImage(provisioningUri),
                setupToken
        );
    }

    /**
     * Verify the TOTP code from the authenticator app during setup.
     * On success, enables MFA and generates backup codes.
     *
     * @param userId   admin user ID
     * @param totpCode current 6-digit code from the authenticator app
     * @return backup codes (one-time-use, shown only once)
     */
    @Transactional
    public SetupCompleteResult completeSetup(String userId, String totpCode) {
        AdminUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
            throw new BadRequestException("MFA setup not initiated — call /mfa/setup first");
        }

        String rawSecret = decryptSecret(user.getMfaSecret());

        if (!verifyCode(rawSecret, totpCode)) {
            log.warn("Invalid TOTP code during MFA setup for userId={}", userId);
            throw new UnauthorizedException("Invalid verification code");
        }

        user.setMfaEnabled(true);
        userRepository.save(user);

        // Generate and store backup codes
        List<BackupCode> backupCodes = generateBackupCodes();
        storeBackupCodes(userId, backupCodes);

        log.info("MFA enabled for userId={}, {} backup codes generated", userId, backupCodes.size());
        return new SetupCompleteResult(backupCodes);
    }

    /**
     * Verify a TOTP code during login.
     *
     * @param userId   admin user ID
     * @param totpCode current 6-digit code from the authenticator app
     * @return true if the code is valid
     */
    @Transactional(readOnly = true)
    public boolean verifyLoginCode(String userId, String totpCode) {
        AdminUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        if (!user.isMfaEnabled() || !MFA_TYPE_TOTP.equals(user.getMfaType())) {
            throw new BadRequestException("MFA is not enabled for this user");
        }

        String rawSecret = decryptSecret(user.getMfaSecret());
        boolean valid = verifyCode(rawSecret, totpCode);

        if (!valid) {
            log.warn("Invalid TOTP code at login for userId={}", userId);
        }
        return valid;
    }

    /**
     * Verify a backup code. Backup codes are single-use.
     *
     * @param userId  admin user ID
     * @param rawCode the code as shown to the user (no spaces/dashes)
     * @return true if the code is valid and has not been used
     */
    @Transactional
    public boolean verifyBackupCode(String userId, String rawCode) {
        String normalized = normalizeBackupCode(rawCode);
        String hashed = hashBackupCode(normalized);

        AdminMfaBackupCode stored = backupCodeRepository
                .findByAdminUserIdAndHashedCode(userId, hashed)
                .orElse(null);

        if (stored == null || stored.getUsedAt() != null) {
            log.warn("Invalid backup code for userId={}", userId);
            return false;
        }

        stored.setUsedAt(Instant.now());
        backupCodeRepository.save(stored);
        log.info("Backup code used for userId={}, codeId={}", userId, stored.getId());
        return true;
    }

    /**
     * Get the current MFA status for a user.
     */
    @Transactional(readOnly = true)
    public MfaStatus getStatus(String userId) {
        AdminUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        long remainingBackupCodes = backupCodeRepository.countByAdminUserIdAndUsedAtIsNull(userId);
        return new MfaStatus(
                user.isMfaEnabled(),
                user.getMfaType(),
                remainingBackupCodes
        );
    }

    /**
     * Disable MFA for a user. Requires the current TOTP code as confirmation.
     */
    @Transactional
    public void disable(String userId, String totpCode) {
        AdminUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        if (!user.isMfaEnabled()) {
            throw new BadRequestException("MFA is not enabled for this user");
        }

        String rawSecret = decryptSecret(user.getMfaSecret());
        if (!verifyCode(rawSecret, totpCode)) {
            throw new UnauthorizedException("Invalid verification code");
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaType(null);
        userRepository.save(user);

        // Also remove all backup codes
        backupCodeRepository.deleteByAdminUserId(userId);

        log.info("MFA disabled for userId={}", userId);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private boolean verifyCode(String rawSecret, String code) {
        try {
            DefaultCodeGenerator generator = new DefaultCodeGenerator(
                    HashingAlgorithm.SHA1, 6);
            DefaultCodeVerifier verifier = new DefaultCodeVerifier(generator, timeProvider);
            // Allow a 1-step skew on either side for clock drift tolerance
            verifier.setTimePeriod(30);
            verifier.setAllowedTimePeriodDiscrepancy(1);
            return verifier.isValidCode(rawSecret, code);
        } catch (Exception e) {
            log.error("TOTP code verification failed unexpectedly", e);
            return false;
        }
    }

    private String buildProvisioningUri(String secret, String account) {
        String label = account.contains("@") ? account : (account + "@" + issuer);
        return String.format(
                "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                label, secret, issuer
        );
    }

    /**
     * Encodes the provisioning URI as a base64 PNG QR code.
     * Uses ZXing's QRCodeWriter at 200x200 pixels.
     */
    private String generateQrCodeImage(String contents) {
        try {
            var writer = new com.google.zxing.qrcode.QRCodeWriter();
            var bitMatrix = writer.encode(contents, com.google.zxing.BarcodeFormat.QR_CODE, 200, 200);
            var bufferedImage = com.google.zxing.client.j2se.MatrixToImageWriter
                    .toBufferedImage(bitMatrix);
            var baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bufferedImage, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    private String encryptSecret(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            SecretKey key = getAesKey();
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("MFA secret encryption failed", e);
        }
    }

    private String decryptSecret(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
            SecretKey key = getAesKey();
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            byte[] plaintext = cipher.doFinal(encrypted);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("MFA secret decryption failed", e);
        }
    }

    /**
     * Derives the AES-256 key from the configured master secret.
     * The config value is expected to be at least 32 chars; if longer it is
     * SHA-256-hashed to produce exactly 32 bytes.
     */
    private SecretKey getAesKey() {
        String configKey = System.getenv("SELFCARE_MFA_ENCRYPTION_KEY");
        if (configKey == null || configKey.isBlank()) {
            // Safe default for dev only. In production this MUST be set.
            configKey = "selfcare-dev-mfa-encryption-key-32b!";
        }
        byte[] keyBytes;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            keyBytes = md.digest(configKey.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private int BACKUP_CODE_BYTES() {
        return 12; // produces 16 base64-url chars; we trim to BACKUP_CODE_LENGTH
    }

    private List<BackupCode> generateBackupCodes() {
        return java.util.stream.IntStream.range(0, BACKUP_CODE_COUNT)
                .mapToObj(i -> {
                    byte[] bytes = new byte[BACKUP_CODE_BYTES()];
                    secureRandom.nextBytes(bytes);
                    String code = Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(bytes)
                            .replace("_", "")
                            .replace("-", "")
                            .substring(0, BACKUP_CODE_LENGTH)
                            .toUpperCase();
                    return new BackupCode(code, hashBackupCode(code));
                })
                .toList();
    }

    private String hashBackupCode(String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(code.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String normalizeBackupCode(String raw) {
        return raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private void storeBackupCodes(String userId, List<BackupCode> codes) {
        // Remove any existing codes first
        backupCodeRepository.deleteByAdminUserId(userId);
        // Insert new codes
        for (BackupCode c : codes) {
            backupCodeRepository.save(AdminMfaBackupCode.builder()
                    .adminUserId(userId)
                    .hashedCode(c.hashedCode())
                    .build());
        }
    }

    // -------------------------------------------------------------------------
    // Test helpers (package-private — only for use by MfaServiceTest)
    // -------------------------------------------------------------------------

    /**
     * Encrypts a raw TOTP secret for use in unit tests.
     * Exposed package-private so the test class can pre-seed an encrypted secret.
     */
    String encryptForTest(String rawSecret) {
        return encryptSecret(rawSecret);
    }

    /**
     * Hashes a backup code for use in unit tests.
     */
    String hashForTest(String rawCode) {
        return hashBackupCode(normalizeBackupCode(rawCode));
    }

    // -------------------------------------------------------------------------
    // DTOs / records
    // -------------------------------------------------------------------------

    public record SetupResult(
            String provisioningUri,
            String qrCodeImage,
            String setupToken
    ) {}

    public record SetupCompleteResult(
            List<BackupCode> backupCodes
    ) {}

    public record BackupCode(
            String code,
            String hashedCode
    ) {}

    public record MfaStatus(
            boolean enabled,
            String type,
            long remainingBackupCodes
    ) {}
}
