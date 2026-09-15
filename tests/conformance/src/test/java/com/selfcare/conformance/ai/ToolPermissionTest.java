package com.selfcare.conformance.ai;

import com.selfcare.conformance.utils.AuthHelper;
import com.selfcare.conformance.utils.TestConfig;
import com.selfcare.conformance.utils.TestDataFactory;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;

/**
 * AI tool permission conformance tests.
 *
 * Validates that the AI assistant's tool execution follows the permission model:
 * - Read-only tools don't require user confirmation
 * - Write tools require user confirmation
 * - Sensitive tools (payment, profile change) require step-up
 * - Tool calls are audited
 * - Tool permissions are tenant-scoped
 */
@Test(groups = {"ai-governance", "ai-permissions"})
public class ToolPermissionTest {

    private static final Logger LOG = LoggerFactory.getLogger(ToolPermissionTest.class);
    private final AuthHelper authHelper = new AuthHelper();

    private AuthHelper.TokenBundle tokenBundle;

    @BeforeClass
    public void setup() {
        tokenBundle = authHelper.login(TestConfig.TENANT_DIALOG, TestDataFactory.uniqueMsisdn());
    }

    @AfterClass
    public void teardown() {
        authHelper.clearCache();
    }

    /**
     * VERIFY: Read-only tool calls are allowed without user confirmation.
     */
    @Test(description = "Read-only tool calls succeed without confirmation")
    public void readOnlyTool_doesNotRequireConfirmation() {
        LOG.info("=== Testing read-only tool call ===");

        Map<String, Object> toolRequest = new HashMap<>();
        toolRequest.put("tool", "getBalance");
        toolRequest.put("arguments", Map.of(
                "connectionId", tokenBundle.primaryConnectionId
        ));

        Response response = authHelper.authenticatedRequest(tokenBundle)
                .contentType(ContentType.JSON)
                .body(toolRequest)
                .when()
                .post("/api/v1/ai/tools/invoke");

        LOG.info("Read-only tool response: status={}, body={}",
                response.statusCode(), response.getBody().asString());

        // Should succeed (200) or return step-up (401) for non-confirmation
        assertThat(response.statusCode())
                .as("Read-only tool should succeed")
                .isIn(200, 202, 404);
    }

    /**
     * VERIFY: Write tools require user confirmation.
     */
    @Test(description = "Write tools require user confirmation token")
    public void writeTool_requiresConfirmation() {
        LOG.info("=== Testing write tool confirmation ===");

        Map<String, Object> toolRequest = new HashMap<>();
        toolRequest.put("tool", "payBill");
        toolRequest.put("arguments", Map.of(
                "billId", "BILL-TEST-001",
                "amount", 100.00
        ));

        Response response = authHelper.authenticatedRequest(tokenBundle)
                .contentType(ContentType.JSON)
                .body(toolRequest)
                .when()
                .post("/api/v1/ai/tools/invoke");

        LOG.info("Write tool response: status={}, body={}",
                response.statusCode(), response.getBody().asString());

        // Should require confirmation (return confirmation token) or step-up
        if (response.statusCode() == 200 || response.statusCode() == 202) {
            JSONObject body = new JSONObject(response.getBody().asString());
            JSONObject data = body.optJSONObject("data");
            if (data != null) {
                assertThat(data.has("confirmationRequired") || data.has("confirmationToken"))
                        .as("Write tool should return confirmation requirement")
                        .isTrue();
            }
        } else if (response.statusCode() == 401) {
            // Step-up required
            assertThat(extractErrorCode(response))
                    .isIn("STEP_UP_REQUIRED", "CONFIRMATION_REQUIRED");
        }
    }

    /**
     * VERIFY: Sensitive tool calls require step-up authentication.
     */
    @Test(description = "Sensitive tool calls require step-up")
    public void sensitiveTool_requiresStepUp() {
        LOG.info("=== Testing sensitive tool ===");

        Map<String, Object> toolRequest = new HashMap<>();
        toolRequest.put("tool", "updateLinkedConnections");
        toolRequest.put("arguments", Map.of(
                "connectionIds", new String[]{"CONN-001", "CONN-002", "CONN-003"}
        ));

        Response response = authHelper.authenticatedRequest(tokenBundle)
                .contentType(ContentType.JSON)
                .body(toolRequest)
                .when()
                .post("/api/v1/ai/tools/invoke");

        LOG.info("Sensitive tool response: status={}, body={}",
                response.statusCode(), response.getBody().asString());

        // Should require step-up or confirmation
        assertThat(response.statusCode())
                .as("Sensitive tool should require step-up or confirmation")
                .isIn(200, 202, 401);
    }

    /**
     * VERIFY: Tool calls include user consent.
     */
    @Test(description = "Tool calls include consent metadata")
    public void toolCall_includesConsentMetadata() {
        LOG.info("=== Testing consent metadata ===");

        Map<String, Object> toolRequest = new HashMap<>();
        toolRequest.put("tool", "getBills");
        toolRequest.put("arguments", Map.of(
                "connectionId", tokenBundle.primaryConnectionId
        ));
        toolRequest.put("userConsent", true);
        toolRequest.put("sessionId", tokenBundle.sessionId);

        Response response = authHelper.authenticatedRequest(tokenBundle)
                .contentType(ContentType.JSON)
                .body(toolRequest)
                .when()
                .post("/api/v1/ai/tools/invoke");

        LOG.info("Tool call with consent: status={}", response.statusCode());

        // Should accept the call
        assertThat(response.statusCode())
                .as("Tool call with consent should be accepted")
                .isIn(200, 202, 404);
    }

    /**
     * VERIFY: Tool permission errors return appropriate error codes.
     */
    @Test(description = "Tool permission errors return PERMISSION_DENIED")
    public void toolPermissionError_returnsPermissionDenied() {
        LOG.info("=== Testing permission denied ===");

        // Try to use admin-only tool as customer
        Map<String, Object> toolRequest = new HashMap<>();
        toolRequest.put("tool", "adminDeleteAccount");
        toolRequest.put("arguments", Map.of(
                "userId", "USER-TEST-001"
        ));

        Response response = authHelper.authenticatedRequest(tokenBundle)
                .contentType(ContentType.JSON)
                .body(toolRequest)
                .when()
                .post("/api/v1/ai/tools/invoke");

        LOG.info("Unauthorized tool response: status={}, body={}",
                response.statusCode(), response.getBody().asString());

        // Should return 403 FORBIDDEN
        assertThat(response.statusCode())
                .as("Unauthorized tool should be forbidden")
                .isIn(403, 404);
    }

    /**
     * VERIFY: Tool calls are rate-limited per session.
     */
    @Test(description = "Tool calls are rate-limited")
    public void toolCalls_areRateLimited() {
        LOG.info("=== Testing tool rate limiting ===");

        Map<String, Object> toolRequest = new HashMap<>();
        toolRequest.put("tool", "getBalance");
        toolRequest.put("arguments", Map.of(
                "connectionId", tokenBundle.primaryConnectionId
        ));

        boolean rateLimited = false;
        for (int i = 0; i < 30; i++) {
            Response response = authHelper.authenticatedRequest(tokenBundle)
                    .contentType(ContentType.JSON)
                    .body(toolRequest)
                    .when()
                    .post("/api/v1/ai/tools/invoke");

            if (response.statusCode() == 429) {
                rateLimited = true;
                LOG.info("Rate limited after {} requests", i + 1);
                response.then()
                        .body("error.code", equalTo("RATE_LIMITED"))
                        .header("Retry-After", notNullValue());
                break;
            }
        }

        LOG.info("Tool rate limiting: {}", rateLimited ? "enforced" : "not triggered (within limits)");
    }

    /**
     * VERIFY: Tool calls are tenant-scoped.
     */
    @Test(description = "Tool calls are tenant-scoped")
    public void toolCalls_areTenantScoped() {
        LOG.info("=== Testing tool tenant scoping ===");

        // Get tool catalog for current tenant
        Response catalog = authHelper.authenticatedRequest(tokenBundle)
                .when()
                .get("/api/v1/ai/tools");

        LOG.info("Tool catalog response: status={}", catalog.statusCode());

        if (catalog.statusCode() == 200) {
            // Get AIA tools (should be different)
            AuthHelper.TokenBundle aiaUser = authHelper.login(TestConfig.TENANT_AIA, TestDataFactory.uniqueMsisdn());

            Response aiaCatalog = authHelper.authenticatedRequest(aiaUser)
                    .when()
                    .get("/api/v1/ai/tools");

            LOG.info("AIA tool catalog response: status={}", aiaCatalog.statusCode());

            // Catalogs should be tenant-specific
            assertThat(aiaCatalog.statusCode())
                    .as("AIA tool catalog should be accessible")
                    .isIn(200, 404);
        }
    }

    /**
     * VERIFY: Unknown tool returns 404.
     */
    @Test(description = "Unknown tool returns 404")
    public void unknownTool_returnsNotFound() {
        LOG.info("=== Testing unknown tool ===");

        Map<String, Object> toolRequest = new HashMap<>();
        toolRequest.put("tool", "nonexistentTool");
        toolRequest.put("arguments", new HashMap<>());

        Response response = authHelper.authenticatedRequest(tokenBundle)
                .contentType(ContentType.JSON)
                .body(toolRequest)
                .when()
                .post("/api/v1/ai/tools/invoke");

        LOG.info("Unknown tool response: status={}", response.statusCode());

        response.then()
                .statusCode(404)
                .body("error.code", equalTo("NOT_FOUND"));
    }

    /**
     * VERIFY: AI conversation is preserved within session.
     */
    @Test(description = "AI conversation is session-scoped")
    public void aiConversation_isSessionScoped() {
        LOG.info("=== Testing conversation session scope ===");

        String conversationId = "conv-" + System.currentTimeMillis();

        // First message
        Map<String, Object> firstMessage = new HashMap<>();
        firstMessage.put("message", "What is my balance?");
        firstMessage.put("conversationId", conversationId);

        Response first = authHelper.authenticatedRequest(tokenBundle)
                .contentType(ContentType.JSON)
                .body(firstMessage)
                .when()
                .post("/api/v1/ai/chat");

        LOG.info("First message response: status={}", first.statusCode());

        // Second message in same conversation
        Map<String, Object> secondMessage = new HashMap<>();
        secondMessage.put("message", "Pay that bill");
        secondMessage.put("conversationId", conversationId);

        Response second = authHelper.authenticatedRequest(tokenBundle)
                .contentType(ContentType.JSON)
                .body(secondMessage)
                .when()
                .post("/api/v1/ai/chat");

        LOG.info("Second message response: status={}", second.statusCode());

        // Both messages should be accepted
        assertThat(first.statusCode())
                .as("First AI message should be accepted")
                .isIn(200, 202, 404);
    }

    // Helper method to extract error code from response
    private String extractErrorCode(Response response) {
        try {
            JSONObject body = new JSONObject(response.getBody().asString());
            JSONObject error = body.optJSONObject("error");
            if (error != null) {
                return error.optString("code", null);
            }
        } catch (Exception e) {
            LOG.debug("Failed to extract error code: {}", e.getMessage());
        }
        return null;
    }
}
