package com.selfcare.config.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NavigationRepositoryImplTest {

    private NavigationRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new NavigationRepositoryImpl(
                org.mockito.Mockito.mock(MongoTemplate.class),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void flattensQuickActionsUsingNestedActionRoute() {
        Map<String, Object> actionPay = new LinkedHashMap<>();
        actionPay.put("type", "NAVIGATE");
        actionPay.put("route", "/bills/pay");
        actionPay.put("journeyId", null);
        actionPay.put("url", null);
        actionPay.put("params", Map.of("from", "home"));
        actionPay.put("analyticsEvent", "quick_pay_bill");

        Map<String, Object> actionRecharge = new LinkedHashMap<>();
        actionRecharge.put("type", "START_JOURNEY");
        actionRecharge.put("journeyId", "recharge-flow");
        actionRecharge.put("route", null);
        actionRecharge.put("url", null);
        actionRecharge.put("params", Map.of());
        actionRecharge.put("analyticsEvent", "quick_recharge");

        Map<String, Object> doc = new HashMap<>();
        doc.put("quickActions", List.of(
                Map.of("id", "pay-bill", "label", "Pay Bill", "icon", "bill", "action", actionPay),
                Map.of("id", "recharge", "label", "Quick Recharge", "icon", "wallet", "action", actionRecharge)
        ));

        List<Map<String, Object>> items = repository.flatten(doc);

        assertEquals(2, items.size());

        Map<String, Object> pay = items.get(0);
        assertEquals("quickActions", pay.get("placement"));
        assertEquals("/bills/pay", pay.get("route"));
        assertEquals(Map.of("from", "home"), pay.get("navParams"));
        assertNotNull(pay.get("action"));

        Map<String, Object> recharge = items.get(1);
        assertEquals("START_JOURNEY", ((Map<?, ?>) recharge.get("action")).get("type"));
        assertEquals("recharge-flow", recharge.get("journeyId"));
        assertEquals("/journey/recharge-flow", recharge.get("route"));
    }

    @Test
    void skipsEntriesWithNoResolvableRoute() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("quickActions", List.of(
                Map.of("id", "no-target", "label", "No Target", "icon", "x")
        ));

        List<Map<String, Object>> items = repository.flatten(doc);

        assertEquals(0, items.size());
    }

    @Test
    void mapsGroupsToPlacements() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("tabs", List.of(Map.of("id", "home", "label", "Home", "icon", "home", "route", "/home")));
        doc.put("drawerItems", List.of(Map.of("id", "settings", "label", "Settings", "route", "/settings")));
        doc.put("routes", List.of(Map.of("id", "profile", "path", "/profile")));

        List<Map<String, Object>> items = repository.flatten(doc);

        assertEquals(3, items.size());
        assertEquals("tabBar", items.get(0).get("placement"));
        assertEquals("/home", items.get(0).get("route"));
        assertEquals("hamburger", items.get(1).get("placement"));
        assertEquals("subPage", items.get(2).get("placement"));
        assertEquals("/profile", items.get(2).get("route"));
    }
}