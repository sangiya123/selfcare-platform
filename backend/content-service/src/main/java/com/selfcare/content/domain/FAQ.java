package com.selfcare.content.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * FAQ — frequently asked questions.
 *
 * Categorized for navigation, searchable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "faqs")
public class FAQ {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    private String category;

    private Map<String, Translation> translations;

    private List<String> tags;

    /** Order within category */
    private Integer displayOrder;

    /** Status: ACTIVE, ARCHIVED */
    private String status;

    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Translation {
        private String question;
        private String answer; // Markdown / HTML
    }
}