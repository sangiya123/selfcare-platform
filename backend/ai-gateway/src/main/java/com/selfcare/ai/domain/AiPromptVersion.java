package com.selfcare.ai.domain;

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
 * Versioned prompt template — implements the prompt/template versioning
 * governance control.
 *
 * Each new version of a prompt template is stored as a new row with a
 * monotonic version number. A/B testing service picks the variant based
 * on experiment configuration; production uses the version marked active.
 *
 * Content is not in this table to keep it lean — content lives in
 * PromptTemplateService's compiled cache; this table is the source of
 * truth and history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_prompt_versions", indexes = {
    @Index(name = "ix_pv_tenant_template", columnList = "tenant_id, template_id"),
    @Index(name = "ix_pv_template_version", columnList = "template_id, version")
})
@EntityListeners(AuditingEntityListener.class)
public class AiPromptVersion {

    @Id
    @Column(name = "prompt_version_id", length = 64)
    private String promptVersionId;

    @Column(name = "tenant_id", length = 32)
    private String tenantId; // null = platform default

    @Column(name = "template_id", nullable = false, length = 64)
    private String templateId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "industry", length = 32)
    private String industry;

    /** SHA-256 of the rendered template body for change detection */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /** Free-form notes about the change */
    @Column(name = "change_notes", length = 1024)
    private String changeNotes;

    /** Whether this is the current production version */
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
