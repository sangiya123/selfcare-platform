package com.omobio.ai.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Versioned AI evaluation set — a curated bundle of test cases used to
 * evaluate a specific use case.
 *
 * Per the AI governance spec: "Every AI use case ships with a versioned
 * evaluation set, baseline score, regression threshold and red-team cases."
 *
 * Sets are immutable once created — new versions are new rows.
 * The {@code isActive} flag controls which version is currently in use.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_evaluation_sets", indexes = {
    @Index(name = "ix_eval_set_tenant_usecase", columnList = "tenant_id, use_case_id"),
    @Index(name = "ix_eval_set_use_case_version", columnList = "use_case_id, version")
})
@EntityListeners(AuditingEntityListener.class)
public class AiEvaluationSet {

    @Id
    @Column(name = "evaluation_set_id", length = 64)
    private String evaluationSetId;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;  // null = platform default

    @Column(name = "use_case_id", nullable = false, length = 64)
    private String useCaseId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "set_type", nullable = false, length = 16)
    private String setType;  // BASELINE, REGRESSION, RED_TEAM, MULTILINGUAL

    @Column(name = "total_cases", nullable = false)
    private Integer totalCases;

    @Column(name = "description", length = 512)
    private String description;

    /** SHA-256 hash of the test-case bundle for change detection. */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
