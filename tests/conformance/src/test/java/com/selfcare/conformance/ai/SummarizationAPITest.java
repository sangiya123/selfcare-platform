package com.selfcare.conformance.ai;

import com.selfcare.conformance.utils.AuthHelper;
import com.selfcare.conformance.utils.TestConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Conversation summarization API conformance tests.
 */
@Test(groups = {"ai", "summarization"})
public class SummarizationAPITest {

    private static final Logger LOG = LoggerFactory.getLogger(SummarizationAPITest.class);
    private final AuthHelper authHelper = new AuthHelper();

    private AuthHelper.TokenBundle user;

    @BeforeClass
    public void setup() {
        RestAssured.baseURI = TestConfig.getGatewayBaseUrl();
        user = authHelper.loginAndGetTokens(TestConfig.Tenant.DIALOG_LK, "dialog-summarization-user");
    }

    @Test
    public void summaryIncludesHeadlineIntentTopics() {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", Arrays.asList(
                message("user", "What is my data balance?"),
                message("assistant", "You have 2.5GB remaining"),
                message("user", "Can I get a data pack recommendation?"),
                message("assistant", "I recommend the 5GB combo pack for Rs. 299")
        ));

        Response resp = RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .header("Authorization", "Bearer " + user.accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/summarize");

        resp.then().statusCode(200);
        String headline = resp.jsonPath().getString("data.headline");
        String intent = resp.jsonPath().getString("data.dominantIntent");
        List<String> topics = resp.jsonPath().getList("data.topics");
        LOG.info("Summary: headline={}, intent={}, topics={}", headline, intent, topics);
        assertThat(headline).isNotEmpty();
        assertThat(intent).isEqualTo("USAGE_INQUIRY");
        assertThat(topics).isNotEmpty();
    }

    @Test
    public void detectsInsuranceIntent() {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", Arrays.asList(
                message("user", "When is my premium due?"),
                message("assistant", "Your next premium is due in 14 days.")
        ));

        Response resp = RestAssured.given()
                .header("X-Tenant-Id", "aia-multi")
                .header("Authorization", "Bearer " + user.accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/summarize");

        resp.then().statusCode(200);
        assertThat(resp.jsonPath().getString("data.dominantIntent")).isEqualTo("INSURANCE");
    }

    @Test
    public void detectsResolutionStatus() {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", Arrays.asList(
                message("user", "My recharge didn't go through"),
                message("assistant", "I'm sorry to hear that. Let me check..."),
                message("user", "It's resolved now, thanks!")
        ));

        Response resp = RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .header("Authorization", "Bearer " + user.accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/summarize");

        resp.then().statusCode(200);
        String status = resp.jsonPath().getString("data.resolutionStatus");
        LOG.info("Resolution status: {}", status);
        assertThat(status).isEqualTo("RESOLVED");
    }

    @Test
    public void emptyConversation_returnsNoConversation() {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", List.of());

        Response resp = RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .header("Authorization", "Bearer " + user.accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/summarize");

        resp.then().statusCode(200);
        assertThat(resp.jsonPath().getString("data.dominantIntent")).isEqualTo("NO_CONVERSATION");
    }

    @Test
    public void requiresAuth() {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", List.of(message("user", "test")));

        RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/summarize")
                .then().statusCode(401);
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
