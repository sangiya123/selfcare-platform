package com.omobio.platform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Common audit fields for any entity.
 *
 * Persisted by every service to support:
 * - Tracking who created/updated a record
 * - Compliance audits
 * - Debugging timeline
 * - Soft-delete and recovery
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Auditable {

    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
    private Long version;
}