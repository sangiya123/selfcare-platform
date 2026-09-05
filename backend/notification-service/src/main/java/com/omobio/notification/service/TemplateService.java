package com.omobio.notification.service;

import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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