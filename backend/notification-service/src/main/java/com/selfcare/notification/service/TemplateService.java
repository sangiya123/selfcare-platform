package com.selfcare.notification.service;

import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Template service — renders notification templates with payload and locale.
 *
 * Templates are stored in MongoDB and can be configured per tenant.
 * Supports simple {{variable}} substitution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final MongoTemplate mongoTemplate;

    public Page<NotificationTemplate> search(String tenantId, String channel, String category, Pageable pageable) {
        Query query = new Query(Criteria.where("tenantId").is(tenantId));
        if (channel != null && !channel.isEmpty()) {
            query.addCriteria(Criteria.where("channels").in(channel));
        }
        if (category != null && !category.isEmpty()) {
            query.addCriteria(Criteria.where("category").is(category));
        }
        query.with(pageable);
        long total = mongoTemplate.count(query, NotificationTemplate.class);
        List<NotificationTemplate> templates = mongoTemplate.find(query, NotificationTemplate.class);
        return new org.springframework.data.domain.PageImpl<>(templates, pageable, total);
    }

    public NotificationTemplate getById(String id) {
        NotificationTemplate template = mongoTemplate.findById(id, NotificationTemplate.class);
        if (template == null) {
            throw new NotFoundException("NotificationTemplate", id);
        }
        return template;
    }

    public NotificationTemplate save(NotificationTemplate template) {
        if (template.getCreatedAt() == null) {
            template.setCreatedAt(Instant.now());
        }
        template.setUpdatedAt(Instant.now());
        return mongoTemplate.save(template);
    }

    public void delete(String id) {
        mongoTemplate.remove(Query.query(Criteria.where("id").is(id)), NotificationTemplate.class);
    }

    public String sendTest(NotificationTemplate template, String recipient, Map<String, Object> payload, String locale) {
        try {
            RenderedTemplate rendered = render(template.getTemplateId(), payload, locale);
            log.info("Test notification sent for template {}: subject={}, body length={}",
                    template.getTemplateId(), rendered.getSubject(), rendered.getBody() != null ? rendered.getBody().length() : 0);
            return "Test notification queued for " + recipient + " (template: " + template.getTemplateId() + ")";
        } catch (Exception e) {
            log.error("Failed to send test notification", e);
            throw e;
        }
    }

    /**
     * Render a template with payload and locale.
     */
    public RenderedTemplate render(String templateId, Map<String, Object> payload, String locale) {
        String tenantId = TenantContext.get().getTenantId();

        // Fetch template
        Query query = new Query(Criteria.where("tenantId").is(tenantId)
                .and("templateId").is(templateId)
                .and("locale").is(locale));
        NotificationTemplate template = mongoTemplate.findOne(query, NotificationTemplate.class);
        if (template == null) {
            // Fallback to default locale (en)
            query = new Query(Criteria.where("tenantId").is(tenantId)
                    .and("templateId").is(templateId)
                    .and("locale").is("en"));
            template = mongoTemplate.findOne(query, NotificationTemplate.class);
        }
        if (template == null) {
            throw new NotFoundException("Template", templateId + ":" + locale);
        }

        // Render subject and body
        String renderedSubject = template.getSubject() != null
                ? substitute(template.getSubject(), payload)
                : null;
        String renderedBody = substitute(template.getBody(), payload);

        return RenderedTemplate.builder()
                .subject(renderedSubject)
                .body(renderedBody)
                .templateId(templateId)
                .locale(locale)
                .build();
    }

    /**
     * Simple {{variable}} substitution.
     * Supports nested paths like {{user.name}}.
     */
    @SuppressWarnings("unchecked")
    private String substitute(String template, Map<String, Object> payload) {
        if (template == null) return null;
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            int open = template.indexOf("{{", i);
            if (open < 0) {
                result.append(template.substring(i));
                break;
            }
            int close = template.indexOf("}}", open);
            if (close < 0) {
                result.append(template.substring(i));
                break;
            }
            result.append(template, i, open);
            String path = template.substring(open + 2, close).trim();
            Object value = resolve(payload, path);
            result.append(value != null ? value : "");
            i = close + 2;
        }
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private Object resolve(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
            if (current == null) return null;
        }
        return current;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RenderedTemplate {
        private String subject;
        private String body;
        private String templateId;
        private String locale;
    }
}