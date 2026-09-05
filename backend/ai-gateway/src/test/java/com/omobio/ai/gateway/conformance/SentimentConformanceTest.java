package com.omobio.ai.gateway.conformance;

import com.omobio.ai.service.SentimentAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Conformance tests for sentiment analysis — verifies the sentiment
 * classification contract:
 *   1. Five-level output (VERY_NEGATIVE, NEGATIVE, NEUTRAL, POSITIVE, VERY_POSITIVE)
 *   2. Negation handling: "not good" → NEGATIVE not POSITIVE
 *   3. Empty/very short text → NEUTRAL
 *   4. Confidence in [0, 1]
 *   5. Triggers returned (for analytics: complaint, churn_risk, etc.)
 *
 * Pure contract test — no Spring context, no external dependencies.
 */
@DisplayName("AI Gateway: Sentiment Analysis Conformance")
class SentimentConformanceTest {

    private SentimentAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new SentimentAnalysisService();
    }

    @Test
    @DisplayName("Five-level sentiment classification")
    void fiveLevelClassification() {
        assertThat(service.analyze("I love this service, it's amazing!").label())
                .isIn("VERY_POSITIVE", "POSITIVE");
        assertThat(service.analyze("It's okay, nothing special.").label())
                .isEqualTo("NEUTRAL");
        assertThat(service.analyze("This is terrible and I'm angry!").label())
                .isIn("VERY_NEGATIVE", "NEGATIVE");
    }

    @Test
    @DisplayName("Negation flips positive to negative")
    void negationHandling() {
        var pos = service.analyze("The service is great!");
        var neg = service.analyze("The service is not great!");
        assertThat(pos.label()).isIn("POSITIVE", "VERY_POSITIVE");
        assertThat(pos.score()).isGreaterThan(0);
        assertThat(neg.label()).isIn("NEGATIVE", "VERY_NEGATIVE");
        assertThat(neg.score()).isLessThan(0);
    }

    @Test
    @DisplayName("Empty or null text returns NEUTRAL with zero score")
    void emptyTextNeutral() {
        assertThat(service.analyze("").label()).isEqualTo("NEUTRAL");
        assertThat(service.analyze(null).label()).isEqualTo("NEUTRAL");
        assertThat(service.analyze("ok").label()).isEqualTo("NEUTRAL");
    }

    @Test
    @DisplayName("Confidence is between 0 and 1")
    void confidenceBounds() {
        var r = service.analyze("Test message");
        assertThat(r.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("Triggers list contains recognized sentiment words")
    void triggerDetection() {
        var r = service.analyze("This is a terrible service, I'm angry and want a refund");
        assertThat(r.triggers()).isNotEmpty();
        assertThat(r.triggers()).containsAnyOf("terrible", "angry");
    }

    @Test
    @DisplayName("Aggregate sentiment produces distribution across all labels")
    void aggregateDistribution() {
        var messages = java.util.List.of(
                "I love this!",
                "It's terrible",
                "Just okay"
        );
        var agg = service.aggregate(messages);
        assertThat(agg.label()).isNotBlank();
        assertThat(agg.distribution()).isNotEmpty();
        assertThat(agg.distribution().keySet())
                .containsAnyOf("POSITIVE", "NEGATIVE", "NEUTRAL");
    }
}
