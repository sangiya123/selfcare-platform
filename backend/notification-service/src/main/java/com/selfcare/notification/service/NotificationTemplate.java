package com.selfcare.notification.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Notification template — stored in MongoDB.
 *
 * Templates support:
 * - Per-tenant configuration
 * - Multi-locale (en, si, ta, etc.)
 * - Channel-specific (PUSH subject vs SMS body)
 * - Variables via {{path}} substitution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notification_templates")
public class NotificationTemplate {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    @Indexed
    private String templateId;

    private String locale;

    private String subject;

    private String body;

    /** Channels this template applies to: PUSH, SMS, EMAIL, IN_APP */
    private List<String> channels;

    /** Category for grouping: TRANSACTIONAL, PROMOTIONAL, OPERATIONAL */
    private String category;

    private Instant createdAt;
    private Instant updatedAt;
}