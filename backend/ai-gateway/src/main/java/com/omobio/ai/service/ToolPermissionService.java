package com.omobio.ai.service;

import com.omobio.platform.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Tool permission service — controls which AI tools each user can access.
 *
 * Permission levels:
 * - NONE: user cannot use this tool
 * - AUTO: tool executes automatically (balance check, list plans)
 * - APPROVAL: tool requires human approval before execution (payment, data change)
 * - DENIED: tool is explicitly blocked
 *
 * Permissions are cached in Redis per user.
 * Admin configures default permissions per tenant in the AI Studio UI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolPermissionService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PERM_PREFIX = "omobio:ai:tools:";

    /**
     * Get all allowed tools for a user.
     * Combines tenant defaults with user-specific overrides.
     */
    public List<ToolDefinition> getAllowedTools(String tenantId, String userId) {
        // Fetch from cache or DB
        String cacheKey = PERM_PREFIX + tenantId + ":" + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List) {
            return (List<ToolDefinition>) cached;
        }

        // Return default tool set
        List<ToolDefinition> defaults = getTenantDefaults(tenantId);
        return defaults;
    }

    /**
     * Check if a specific tool is permitted for a user.
     */
    public ToolPermission getPermission(String tenantId, String userId, String toolName) {
        List<ToolDefinition> tools = getAllowedTools(tenantId, userId);
        for (ToolDefinition tool : tools) {
            if (tool.getName().equals(toolName)) {
                return tool.getPermission();
            }
        }
        return ToolPermission.DENIED;
    }

    /**
     * Set a tool permission for a user.
     */
    public void setPermission(String tenantId, String userId, String toolName, ToolPermission perm) {
        String cacheKey = PERM_PREFIX + tenantId + ":" + userId;
        List<ToolDefinition> tools = getAllowedTools(tenantId, userId);
        // Update tool in list or add new
        boolean found = false;
        for (ToolDefinition tool : tools) {
            if (tool.getName().equals(toolName)) {
                tool.setPermission(perm);
                found = true;
                break;
            }
        }
        if (!found) {
            tools.add(ToolDefinition.builder()
                    .name(toolName)
                    .permission(perm)
                    .build());
        }
        redisTemplate.opsForValue().set(cacheKey, tools);
        log.info("Tool permission set: user={}, tool={}, perm={}", userId, toolName, perm);
    }

    /**
     * Default tools for a tenant.
     */
    private List<ToolDefinition> getTenantDefaults(String tenantId) {
        // In production: fetch from database per tenant config
        return Arrays.asList(
                ToolDefinition.builder()
                        .name("get_balance")
                        .description("Check user's current balance")
                        .permission(ToolPermission.AUTO)
                        .inputSchema(Map.of("type", "object", "properties",
                                Map.of("connectionId", Map.of("type", "string"))))
                        .build(),
                ToolDefinition.builder()
                        .name("get_usage")
                        .description("Get usage summary (data, voice, SMS)")
                        .permission(ToolPermission.AUTO)
                        .inputSchema(Map.of("type", "object", "properties",
                                Map.of("connectionId", Map.of("type", "string"))))
                        .build(),
                ToolDefinition.builder()
                        .name("list_plans")
                        .description("List available plans and packages")
                        .permission(ToolPermission.AUTO)
                        .inputSchema(Map.of("type", "object", "properties",
                                Map.of("category", Map.of("type", "string", "enum", Arrays.asList("DATA", "VOICE", "COMBO", "ALL")))))
                        .build(),
                ToolDefinition.builder()
                        .name("recommend_plan")
                        .description("Get ML-based plan recommendation")
                        .permission(ToolPermission.AUTO)
                        .inputSchema(Map.of("type", "object", "properties",
                                Map.of("connectionId", Map.of("type", "string"))))
                        .build(),
                ToolDefinition.builder()
                        .name("create_support_ticket")
                        .description("Create a support ticket")
                        .permission(ToolPermission.AUTO)
                        .inputSchema(Map.of("type", "object", "properties",
                                Map.of("subject", Map.of("type", "string"),
                                        "description", Map.of("type", "string"),
                                        "priority", Map.of("type", "string", "enum", Arrays.asList("LOW", "MEDIUM", "HIGH")))))
                        .build(),
                ToolDefinition.builder()
                        .name("pay_bill")
                        .description("Pay a bill")
                        .permission(ToolPermission.APPROVAL)
                        .inputSchema(Map.of("type", "object", "properties",
                                Map.of("billId", Map.of("type", "string"),
                                        "amount", Map.of("type", "number"))))
                        .build(),
                ToolDefinition.builder()
                        .name("recharge")
                        .description("Top up prepaid balance")
                        .permission(ToolPermission.APPROVAL)
                        .inputSchema(Map.of("type", "object", "properties",
                                Map.of("amount", Map.of("type", "number"),
                                        "paymentMethodId", Map.of("type", "string"))))
                        .build()
        );
    }

    public enum ToolPermission {
        NONE,      // Not available
        AUTO,      // Executes automatically
        APPROVAL,  // Queued for human approval
        DENIED     // Explicitly blocked
    }

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class ToolDefinition {
        private String name;
        private String description;
        private ToolPermission permission;
        private Map<String, Object> inputSchema;
    }
}
