package com.omobio.admin.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * One-time backup code for admin MFA.
 *
 * Codes are stored as SHA-256 hashes; the plaintext is shown to the user only
 * once at MFA setup time. Used codes are marked with {@code used_at}.
 *
 * @see MfaService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "admin_mfa_backup_codes", indexes = {
    @Index(name = "ix_backup_codes_user", columnList = "admin_user_id")
})
@EntityListeners(AuditingEntityListener.class)
public class AdminMfaBackupCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false, length = 64)
    private String adminUserId;

    @Column(name = "hashed_code", nullable = false, length = 128)
    private String hashedCode;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
