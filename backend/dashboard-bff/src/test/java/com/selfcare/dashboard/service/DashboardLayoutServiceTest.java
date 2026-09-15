package com.selfcare.dashboard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies the DB-backed dashboard layout resolution (the platform analogue of
 * the legacy midend dashboard layout):
 * - Redis cache miss falls through to MongoDB
 * - Profile-specific layout wins over the tenant-level fallback
 * - Missing/erroring Mongo falls back to the industry default (TELCO)
 */
@ExtendWith(MockitoExtension.class)
class DashboardLayoutServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private MongoTemplate mongoTemplate;

    private DashboardLayoutService service;

    @BeforeEach
    void setUp() {
        service = new DashboardLayoutService(redisTemplate, mongoTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
    }

    @Test
    @DisplayName("Resolves profile-specific layout from MongoDB when cache is cold")
    void resolvesProfileLayoutFromMongo() {
        DashboardLayoutDocument doc = DashboardLayoutDocument.builder()
                .tenantId("dialog-lk")
                .profileKey("PREPAID")
                .active(true)
                .widgetOrder(List.of("balance", "usage", "quick-addon-and-reload", "banners"))
                .environment("dev")
                .status("PUBLISHED")
                .build();
        when(mongoTemplate.findOne(any(Query.class), eq(DashboardLayoutDocument.class))).thenReturn(doc);

        List<String> layout = service.resolveLayout("dialog-lk", "PREPAID");

        assertThat(layout).containsExactly("balance", "usage", "quick-addon-and-reload", "banners");
    }

    @Test
    @DisplayName("Falls back to the industry default when no layout is authored")
    void fallsBackToDefaultWhenMissing() {
        when(mongoTemplate.findOne(any(Query.class), eq(DashboardLayoutDocument.class))).thenReturn(null);

        List<String> layout = service.resolveLayout("dialog-lk", "PREPAID");

        assertThat(layout)
                .contains("balance", "usage", "bill", "quick-actions", "notifications", "bundles", "banners");
    }

    @Test
    @DisplayName("Falls back to the industry default when MongoDB is unreachable")
    void fallsBackToDefaultWhenMongoFails() {
        when(mongoTemplate.findOne(any(Query.class), eq(DashboardLayoutDocument.class)))
                .thenThrow(new RuntimeException("connection refused"));

        List<String> layout = service.resolveLayout("dialog-lk", "POSTPAID");

        assertThat(layout).contains("balance", "usage", "bill");
    }
}