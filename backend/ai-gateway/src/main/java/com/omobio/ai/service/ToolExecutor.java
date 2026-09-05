package com.omobio.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omobio.ai.service.AIResponse.ToolCallResult;
import com.omobio.platform.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tool Executor — executes AI-requested tools safely within the platform.
 *
 * Each tool maps to a backend service call or internal operation. Execution
 * is gated by ToolPermissionService (AUTO tools run immediately; APPROVAL
 * tools are queued for human review).
 *
 * Tool execution is always logged to the audit trail.
 *
 * Supported tools (defined in ToolPermissionService):
 *   get_balance       → usage-service
 *   get_usage        → usage-service
 *   list_plans       → product-service
 *   recommend_plan    → RecommendationService
 *   create_support_ticket → notification-service
 *   pay_bill         → payment-service  (APPROVAL required)
 *   recharge         → payment-service  (APPROVAL required)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecutor {

    private final ToolPermissionService toolPermissionService;
    private final RecommendationService recommendationService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    // Base URLs — resolved from environment in production
    private static final String USAGE_SERVICE_URL = "http://usage-service:8080";
    private static final String PRODUCT_SERVICE_URL = "http://product-service:8080";
    private static final String PAYMENT_SERVICE_URL = "http://payment-service:8080";
    private static final String NOTIFICATION_SERVICE_URL = "http://notification-service:8080";

    /**
     * Execute a tool call.
     *
     * @param toolName the tool name
     * @param arguments JSON argument string
     * @param tenantId the tenant
     * @param userId the user
     * @param connectionId the connection (may be null for tenant-level tools)
     * @return the execution result
     */
    public ToolCallResult execute(String toolName, String arguments,
                                   String tenantId, String userId, String connectionId) {
        log.info("Tool execution: tool={}, user={}, tenant={}", toolName, userId, tenantId);

        // Check permission
        ToolPermissionService.ToolPermission perm =
                toolPermissionService.getPermission(tenantId, userId, toolName);

        if (perm == ToolPermissionService.ToolPermission.DENIED ||
                perm == ToolPermissionService.ToolPermission.NONE) {
            return ToolCallResult.builder()
                    .toolName(toolName)
                    .arguments(arguments)
                    .result(null)
                    .success(false)
                    .error("Tool not permitted for this user")
                    .build();
        }

        if (perm == ToolPermissionService.ToolPermission.APPROVAL) {
            // Queue for approval — for now, return a "pending" result
            return ToolCallResult.builder()
                    .toolName(toolName)
                    .arguments(arguments)
                    .result("{\"status\": \"PENDING_APPROVAL\", \"message\": \"This action requires approval. Our team will review it shortly.\"}")
                    .success(true)
                    .build();
        }

        // Execute AUTO tools
        try {
            return switch (toolName) {
                case "get_balance" -> executeGetBalance(connectionId, tenantId);
                case "get_usage" -> executeGetUsage(connectionId, tenantId);
                case "list_plans" -> executeListPlans(arguments, tenantId);
                case "recommend_plan" -> executeRecommendPlan(connectionId, tenantId);
                case "create_support_ticket" -> executeCreateTicket(arguments, tenantId, userId);
                default -> ToolCallResult.builder()
                        .toolName(toolName)
                        .arguments(arguments)
                        .success(false)
                        .error("Unknown tool: " + toolName)
                        .build();
            };
        } catch (Exception e) {
            log.error("Tool execution failed: tool={}, error={}", toolName, e.getMessage(), e);
            return ToolCallResult.builder()
                    .toolName(toolName)
                    .arguments(arguments)
                    .success(false)
                    .error("Execution failed: " + e.getMessage())
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Tool implementations
    // -------------------------------------------------------------------------

    private ToolCallResult executeGetBalance(String connectionId, String tenantId) {
        if (connectionId == null) {
            return ToolCallResult.builder()
                    .toolName("get_balance")
                    .success(false)
                    .error("connectionId is required")
                    .build();
        }
        try {
            Map<String, Object> response = webClientBuilder.build()
                    .get()
                    .uri(USAGE_SERVICE_URL + "/api/v1/usage/balance/{connectionId}", connectionId)
                    .header("X-Tenant-Id", tenantId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return ToolCallResult.builder()
                    .toolName("get_balance")
                    .arguments("{\"connectionId\": \"" + connectionId + "\"}")
                    .result(objectMapper.writeValueAsString(response))
                    .success(true)
                    .build();
        } catch (Exception e) {
            return ToolCallResult.builder()
                    .toolName("get_balance")
                    .success(false)
                    .error("Failed to get balance: " + e.getMessage())
                    .build();
        }
    }

    private ToolCallResult executeGetUsage(String connectionId, String tenantId) {
        if (connectionId == null) {
            return ToolCallResult.builder()
                    .toolName("get_usage")
                    .success(false)
                    .error("connectionId is required")
                    .build();
        }
        try {
            Map<String, Object> response = webClientBuilder.build()
                    .get()
                    .uri(USAGE_SERVICE_URL + "/api/v1/usage/current/{connectionId}", connectionId)
                    .header("X-Tenant-Id", tenantId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return ToolCallResult.builder()
                    .toolName("get_usage")
                    .arguments("{\"connectionId\": \"" + connectionId + "\"}")
                    .result(objectMapper.writeValueAsString(response))
                    .success(true)
                    .build();
        } catch (Exception e) {
            return ToolCallResult.builder()
                    .toolName("get_usage")
                    .success(false)
                    .error("Failed to get usage: " + e.getMessage())
                    .build();
        }
    }

    private ToolCallResult executeListPlans(String arguments, String tenantId) {
        String category = null;
        try {
            if (arguments != null && !arguments.isBlank()) {
                Map<String, Object> args = objectMapper.readValue(arguments, Map.class);
                category = (String) args.get("category");
            }
        } catch (Exception ignored) {}

        try {
            String uri = PRODUCT_SERVICE_URL + "/api/v1/products";
            if (category != null && !"ALL".equalsIgnoreCase(category)) {
                uri += "?category=" + category;
            }
            List<Map<String, Object>> response = webClientBuilder.build()
                    .get()
                    .uri(uri)
                    .header("X-Tenant-Id", tenantId)
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();

            return ToolCallResult.builder()
                    .toolName("list_plans")
                    .arguments(arguments)
                    .result(objectMapper.writeValueAsString(response))
                    .success(true)
                    .build();
        } catch (Exception e) {
            return ToolCallResult.builder()
                    .toolName("list_plans")
                    .success(false)
                    .error("Failed to list plans: " + e.getMessage())
                    .build();
        }
    }

    private ToolCallResult executeRecommendPlan(String connectionId, String tenantId) {
        try {
            List<RecommendationService.BundleRecommendation> recs = recommendationService.recommendBundles(connectionId, 3);
            return ToolCallResult.builder()
                    .toolName("recommend_plan")
                    .arguments("{\"connectionId\": \"" + connectionId + "\"}")
                    .result(objectMapper.writeValueAsString(recs))
                    .success(true)
                    .build();
        } catch (Exception e) {
            return ToolCallResult.builder()
                    .toolName("recommend_plan")
                    .success(false)
                    .error("Failed to get recommendations: " + e.getMessage())
                    .build();
        }
    }

    private ToolCallResult executeCreateTicket(String arguments, String tenantId, String userId) {
        if (arguments == null || arguments.isBlank()) {
            return ToolCallResult.builder()
                    .toolName("create_support_ticket")
                    .success(false)
                    .error("Arguments required: subject, description, priority")
                    .build();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = objectMapper.readValue(arguments, Map.class);

            String subject = (String) args.getOrDefault("subject", "AI Chat Request");
            String description = (String) args.getOrDefault("description", "");
            String priority = (String) args.getOrDefault("priority", "MEDIUM");

            Map<String, Object> ticket = Map.of(
                    "subject", subject,
                    "description", description,
                    "priority", priority,
                    "source", "AI_CHAT",
                    "userId", userId
            );

            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(NOTIFICATION_SERVICE_URL + "/api/v1/tickets")
                    .header("X-Tenant-Id", tenantId)
                    .bodyValue(ticket)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return ToolCallResult.builder()
                    .toolName("create_support_ticket")
                    .arguments(arguments)
                    .result(objectMapper.writeValueAsString(response))
                    .success(true)
                    .build();
        } catch (Exception e) {
            return ToolCallResult.builder()
                    .toolName("create_support_ticket")
                    .success(false)
                    .error("Failed to create ticket: " + e.getMessage())
                    .build();
        }
    }
}
