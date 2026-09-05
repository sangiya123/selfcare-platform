package com.omobio.admin.identity.service;

import com.omobio.admin.domain.AdminMfaBackupCode;
import com.omobio.admin.domain.AdminUser;
import com.omobio.admin.repository.AdminMfaBackupCodeRepository;
import com.omobio.admin.repository.AdminUserRepository;
import com.omobio.admin.service.MfaService;
import com.omobio.platform.common.web.BadRequestException;
import com.omobio.platform.common.web.NotFoundException;
import com.omobio.platform.common.web.UnauthorizedException;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultTotpHashProvider;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MfaService}.
 *
 * Verifies the TOTP setup, verification, backup-code, and disable flows.
 */
@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

    @Mock private AdminUserRepository userRepository;
    @Mock private AdminMfaBackupCodeRepository backupCodeRepository;

    private MfaService service;

    @BeforeEach
    void setUp() {
        service = new MfaService(userRepository, backupCodeRepository);
        ReflectionTestUtils.setField(service, "issuer", "OMOBIO");
    }

    @Test
    @DisplayName("beginSetup generates a QR code, encrypts the secret, and stores it on the user")
    void beginSetup() {
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .role("SUPER_ADMIN")
                .status("ACTIVE")
                .mfaEnabled(false)
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(AdminUser.class))).thenAnswer(i -> i.getArgument(0));

        MfaService.SetupResult result = service.beginSetup("user-1");

        assertThat(result.provisioningUri()).startsWith("otpauth://totp/");
        assertThat(result.provisioningUri()).contains("secret=");
        assertThat(result.qrCodeImage()).isNotBlank();
        assertThat(result.setupToken()).isNotBlank();
        // Secret is stored encrypted (not the raw secret)
        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        verify(userRepository).save(captor.capture());
        AdminUser saved = captor.getValue();
        assertThat(saved.getMfaType()).isEqualTo("TOTP");
        assertThat(saved.getMfaSecret()).isNotBlank();
        assertThat(saved.isMfaEnabled()).isFalse(); // not enabled until verify-setup
    }

    @Test
    @DisplayName("beginSetup throws NotFoundException for unknown user")
    void beginSetup_unknownUser() {
        when(userRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.beginSetup("unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("completeSetup with correct TOTP code enables MFA and stores backup codes")
    void completeSetup_success() throws Exception {
        // We need to generate a real TOTP code from the secret
        String rawSecret = "JBSWY3DPEHPK3PXP";
        // Encrypt the raw secret using the service's encryption
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .role("SUPER_ADMIN")
                .status("ACTIVE")
                .mfaType("TOTP")
                .mfaSecret(service.encryptForTest(rawSecret))
                .mfaEnabled(false)
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(AdminUser.class))).thenAnswer(i -> i.getArgument(0));
        when(backupCodeRepository.save(any(AdminMfaBackupCode.class)))
                .thenAnswer(i -> i.getArgument(0));

        // Generate a valid TOTP code using the same library
        String totpCode = generateTotp(rawSecret);

        MfaService.SetupCompleteResult result = service.completeSetup("user-1", totpCode);

        assertThat(result.backupCodes()).hasSize(10);
        assertThat(result.backupCodes().get(0).code()).isNotBlank();
        assertThat(result.backupCodes().get(0).hashedCode()).isNotBlank();
        // MFA is now enabled
        ArgumentCaptor<AdminUser> userCaptor = ArgumentCaptor.forClass(AdminUser.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        AdminUser lastSaved = userCaptor.getAllValues().get(userCaptor.getAllValues().size() - 1);
        assertThat(lastSaved.isMfaEnabled()).isTrue();
        // Backup codes were saved
        verify(backupCodeRepository, times(10)).save(any(AdminMfaBackupCode.class));
    }

    @Test
    @DisplayName("completeSetup throws UnauthorizedException for invalid TOTP code")
    void completeSetup_wrongCode() {
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .mfaType("TOTP")
                .mfaSecret(service.encryptForTest("JBSWY3DPEHPK3PXP"))
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.completeSetup("user-1", "000000"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("completeSetup throws BadRequestException when setup was not initiated")
    void completeSetup_notInitiated() {
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .mfaSecret(null)
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.completeSetup("user-1", "123456"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("verifyLoginCode with correct TOTP returns true")
    void verifyLoginCode_success() {
        String rawSecret = "JBSWY3DPEHPK3PXP";
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .mfaType("TOTP")
                .mfaSecret(service.encryptForTest(rawSecret))
                .mfaEnabled(true)
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        String totpCode = generateTotp(rawSecret);
        boolean valid = service.verifyLoginCode("user-1", totpCode);

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("verifyLoginCode with wrong TOTP returns false")
    void verifyLoginCode_wrong() {
        String rawSecret = "JBSWY3DPEHPK3PXP";
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .mfaType("TOTP")
                .mfaSecret(service.encryptForTest(rawSecret))
                .mfaEnabled(true)
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        boolean valid = service.verifyLoginCode("user-1", "000000");

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("verifyLoginCode throws BadRequestException when MFA is not enabled")
    void verifyLoginCode_notEnabled() {
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .mfaEnabled(false)
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.verifyLoginCode("user-1", "123456"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("verifyBackupCode marks the code as used and returns true")
    void verifyBackupCode_success() {
        String rawCode = "ABCDEFGHIJ";
        // The service normalizes to upper + strips non-alnum, then hashes
        String hashed = service.hashForTest(rawCode);

        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .mfaEnabled(true)
                .build();
        AdminMfaBackupCode stored = AdminMfaBackupCode.builder()
                .id(1L)
                .adminUserId("user-1")
                .hashedCode(hashed)
                .build();
        when(backupCodeRepository.findByAdminUserIdAndHashedCode("user-1", hashed))
                .thenReturn(Optional.of(stored));
        when(backupCodeRepository.save(any(AdminMfaBackupCode.class)))
                .thenAnswer(i -> i.getArgument(0));

        boolean valid = service.verifyBackupCode("user-1", "abc-def-ghij");

        assertThat(valid).isTrue();
        assertThat(stored.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("verifyBackupCode returns false for already-used code")
    void verifyBackupCode_alreadyUsed() {
        String rawCode = "ABCDEFGHIJ";
        String hashed = service.hashForTest(rawCode);

        AdminMfaBackupCode stored = AdminMfaBackupCode.builder()
                .id(1L)
                .adminUserId("user-1")
                .hashedCode(hashed)
                .usedAt(java.time.Instant.now().minusSeconds(60))
                .build();
        when(backupCodeRepository.findByAdminUserIdAndHashedCode("user-1", hashed))
                .thenReturn(Optional.of(stored));

        boolean valid = service.verifyBackupCode("user-1", "ABCDEFGHIJ");

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("verifyBackupCode returns false for unknown code")
    void verifyBackupCode_unknown() {
        when(backupCodeRepository.findByAdminUserIdAndHashedCode(anyString(), anyString()))
                .thenReturn(Optional.empty());

        boolean valid = service.verifyBackupCode("user-1", "INVALIDCODE");

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("getStatus returns enabled=false when MFA is not set up")
    void getStatus_disabled() {
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .mfaEnabled(false)
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        MfaService.MfaStatus status = service.getStatus("user-1");

        assertThat(status.enabled()).isFalse();
        assertThat(status.type()).isNull();
    }

    @Test
    @DisplayName("disable clears MFA state and deletes backup codes")
    void disable() {
        String rawSecret = "JBSWY3DPEHPK3PXP";
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .mfaType("TOTP")
                .mfaSecret(service.encryptForTest(rawSecret))
                .mfaEnabled(true)
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(AdminUser.class))).thenAnswer(i -> i.getArgument(0));

        String totpCode = generateTotp(rawSecret);
        service.disable("user-1", totpCode);

        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        verify(userRepository).save(captor.capture());
        AdminUser saved = captor.getValue();
        assertThat(saved.isMfaEnabled()).isFalse();
        assertThat(saved.getMfaSecret()).isNull();
        assertThat(saved.getMfaType()).isNull();
        verify(backupCodeRepository).deleteByAdminUserId("user-1");
    }

    @Test
    @DisplayName("disable throws UnauthorizedException for wrong TOTP code")
    void disable_wrongCode() {
        String rawSecret = "JBSWY3DPEHPK3PXP";
        AdminUser user = AdminUser.builder()
                .id("user-1")
                .email("admin@omobio.com")
                .mfaType("TOTP")
                .mfaSecret(service.encryptForTest(rawSecret))
                .mfaEnabled(true)
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.disable("user-1", "000000"))
                .isInstanceOf(UnauthorizedException.class);
    }

    /**
     * Generate a valid TOTP code using the same library the service uses.
     * Allows tests to verify the actual code path, not just mock it.
     */
    private String generateTotp(String secret) throws Exception {
        var timeProvider = new SystemTimeProvider();
        var generator = new DefaultCodeGenerator(new DefaultTotpHashProvider(), timeProvider);
        long bucket = timeProvider.getTime() / 30;
        return generator.generate(secret, bucket);
    }
}
