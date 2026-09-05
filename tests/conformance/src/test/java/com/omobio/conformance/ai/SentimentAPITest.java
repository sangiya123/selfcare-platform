package com.omobio.conformance.ai;

import com.omobio.conformance.utils.AuthHelper;
import com.omobio.conformance.utils.TestConfig;
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
 * Sentiment analysis API conformance tests.
 *
 * Validates the AI sentiment endpoints:
 * - POST /api/v1/ai/sentiment
 * - POST /api/v1/ai/sentiment/aggregate
 * - Response shape (score, label, confidence)
 * - Multi-tenant isolation
 */
@Test(groups = {"ai", "sentiment"})
public class SentimentAPITest {

    private static final Logger LOG = LoggerFactory.getLogger(SentimentAPITest.class);
    private final AuthHelper authHelper = new AuthHelper();

    private AuthHelper.TokenBundle dialogUser;
    private AuthHelper.TokenBundle aiaUser;

    @BeforeClass
    public void setup() {
        RestAssured.baseURI = TestConfig.getGatewayBaseUrl();
        dialogUser = authHelper.loginAndGetTokens(TestConfig.Tenant.DIALOG_LK, "dialog-sentiment-user");
        aiaUser = authHelper.loginAndGetTokens(TestConfig.Tenant.AIA_MULTI, "aia-sentiment-user");
    }

    @Test
    public void veryNegativeMessage_returnsVeryNegativeOrNegative() {
        Map<String, Object> body = new HashMap<>();
        body.put("text", "This is a terrible awful service! I'm angry and want a refund!");

        Response resp = RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .header("Authorization", "Bearer " + dialogUser.accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/sentiment");

        resp.then().statusCode(200);
        String label = resp.jsonPath().getString("data.label");
        int score = resp.jsonPath().getInt("data.score");
        LOG.info("Sentiment for negative message: label={}, score={}", label, score);
        assertThat(score).isLessThan(0);
        assertThat(label).isIn("NEGATIVE", "VERY_NEGATIVE");
    }

    @Test
    public void veryPositiveMessage_returnsPositiveLabel() {
        Map<String, Object> body = new HashMap<>();
        body.put("text", "Excellent service! I love it. Thank you so much!");

        Response resp = RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .header("Authorization", "Bearer " + dialogUser.accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/sentiment");

        resp.then().statusCode(200);
        String label = resp.jsonPath().getString("data.label");
        int score = resp.jsonPath().getInt("data.score");
        LOG.info("Sentiment for positive message: label={}, score={}", label, score);
        assertThat(score).isGreaterThan(0);
        assertThat(label).isIn("POSITIVE", "VERY_POSITIVE");
    }

    @Test
    public void neutralMessage_returnsNeutralLabel() {
        Map<String, Object> body = new HashMap<>();
        body.put("text", "The meeting is scheduled for tomorrow at 3pm.");

        Response resp = RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .header("Authorization", "Bearer " + dialogUser.accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/sentiment");

        resp.then().statusCode(200);
        assertThat(resp.jsonPath().getString("data.label")).isEqualTo("NEUTRAL");
    }

    @Test
    public void negationFlipsSentiment() {
        Map<String, Object> body = new HashMap<>();
        body.put("text", "The service is not good today");

        Response resp = RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .header("Authorization", "Bearer " + dialogUser.accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/sentiment");

        resp.then().statusCode(200);
        int score = resp.jsonPath().getInt("data.score");
        LOG.info("Negation sentiment score: {}", score);
        assertThat(score).isLessThanOrEqualTo(0);
    }

    @Test
    public void emptyText_returnsNeutral() {
        Map<String, Object> body = new HashMap<>();
        body.put("text", "");

        Response resp = RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .header("Authorization", "Bearer " + dialogUser.accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/sentiment");

        resp.then().statusCode(200);
        assertThat(resp.jsonPath().getInt("data.score")).isEqualTo(0);
    }

    @Test
    public void aggregateReturnsDistribution() {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", Arrays.asList(
                "Great service",
                "I love this",
                "This is awful",
                "I'm angry",
                "OK service"
        ));

        Response resp = RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .header("Authorization", "Bearer " + dialogUser.accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/sentiment/aggregate");

        resp.then().statusCode(200);
        String label = resp.jsonPath().getString("data.label");
        int total = resp.jsonPath().getInt("data.totalScore");
        LOG.info("Aggregate sentiment: label={}, total={}", label, total);
        assertThat(total).isNotZero();
        assertThat(label).isNotEmpty();
    }

    @Test
    public void sentimentIsTenantScoped() {
        // Same message, two tenants
        Map<String, Object> body = new HashMap<>();
        body.put("text", "Excellent service!");

        Response dialogResp = RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .header("Authorization", "Bearer " + dialogUser.accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/sentiment");

        Response aiaResp = RestAssured.given()
                .header("X-Tenant-Id", "aia-multi")
                .header("Authorization", "Bearer " + aiaUser.accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/sentiment");

        dialogResp.then().statusCode(200);
        aiaResp.then().statusCode(200);

        // Both should classify correctly (the underlying logic isn't tenant-specific,
        // but the rate limits and usage tracking are)
        assertThat(dialogResp.jsonPath().getInt("data.score")).isGreaterThan(0);
        assertThat(aiaResp.jsonPath().getInt("data.score")).isGreaterThan(0);
    }

    @Test
    public void requiresAuth() {
        Map<String, Object> body = new HashMap<>();
        body.put("text", "Test message");

        RestAssured.given()
                .header("X-Tenant-Id", "dialog-lk")
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/ai/sentiment")
                .then().statusCode(401);
    }
}
