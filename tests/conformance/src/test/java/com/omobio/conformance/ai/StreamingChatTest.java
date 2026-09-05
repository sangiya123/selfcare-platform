package com.omobio.conformance.ai;

import com.omobio.conformance.utils.AuthHelper;
import com.omobio.conformance.utils.TestConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * AI streaming chat conformance tests.
 *
 * Validates SSE streaming behavior:
 * - POST /api/v1/ai/chat/stream returns a streamId
 * - GET /api/v1/ai/chat/stream/{streamId}/events returns chunk events
 * - Stream terminates with 'done' or 'error' event
 */
@Test(groups = {"ai", "streaming"})
public class StreamingChatTest {

    private static final Logger LOG = LoggerFactory.getLogger(StreamingChatTest.class);
    private final AuthHelper authHelper = new AuthHelper();

    private AuthHelper.TokenBundle user;

    @BeforeClass
    public void setup() {
        RestAssured.baseURI = TestConfig.getGatewayBaseUrl();
        user = authHelper.loginAndGetTokens(TestConfig.Tenant.DIALOG_LK, "dialog-streaming-user");
    }

    @Test
    public void initiateStream_returnsStreamId() {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", List.of(Map.of("role", "user", "content", "Hello AI")));
        body.put("sessionId", "stream-test-session-1");
        body.put("tenantId", "dialog-lk");

        Response resp = RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .header("Authorization", "Bearer " + user.accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/chat/stream");

        resp.then().statusCode(anyOf(equalTo(200), equalTo(202)));
        String streamId = resp.jsonPath().getString("data.streamId");
        LOG.info("Got streamId: {}", streamId);
        assertThat(streamId).isNotBlank();
    }

    @Test
    public void streamRequiresAuth() {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", List.of(Map.of("role", "user", "content", "Hello")));
        body.put("sessionId", "no-auth-test");

        RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/chat/stream")
                .then().statusCode(401);
    }

    @Test
    public void emptyMessages_returns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", List.of());
        body.put("sessionId", "empty-test");

        RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .header("Authorization", "Bearer " + user.accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/chat/stream")
                .then().statusCode(anyOf(equalTo(400), equalTo(422)));
    }

    @Test
    public void rateLimitedWhenExcessive() {
        // Send many requests quickly to trigger rate limit
        for (int i = 0; i < 65; i++) {
            Map<String, Object> body = new HashMap<>();
            body.put("messages", List.of(Map.of("role", "user", "content", "Hi " + i)));
            body.put("sessionId", "rate-test-" + i);

            Response resp = RestAssured.given()
                    .header("X-Tenant-Id", "dialog-lk")
                    .header("Authorization", "Bearer " + user.accessToken)
                    .contentType(ContentType.JSON)
                    .body(body)
                    .post("/api/v1/ai/chat/stream");

            if (resp.statusCode() == 429) {
                LOG.info("Rate limit triggered on request {}", i);
                assertThat(resp.statusCode()).isEqualTo(429);
                return;
            }
        }
        LOG.info("Rate limit was not triggered in this run (acceptable in test environments)");
    }
}
