package com.selfcare.ai.gateway.service;

import com.selfcare.ai.service.SentimentAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SentimentAnalysisServiceTest {

    private SentimentAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new SentimentAnalysisService();
    }

    @Test
    @DisplayName("returns neutral for empty text")
    void emptyText() {
        var result = service.analyze("");
        assertThat(result.score()).isEqualTo(0);
        assertThat(result.label()).isEqualTo("NEUTRAL");
    }

    @Test
    @DisplayName("returns neutral for null text")
    void nullText() {
        var result = service.analyze(null);
        assertThat(result.score()).isEqualTo(0);
    }

    @Test
    @DisplayName("detects very negative text")
    void veryNegative() {
        var result = service.analyze("This is a terrible awful service, I'm angry and want a refund");
        assertThat(result.label()).isIn("VERY_NEGATIVE", "NEGATIVE");
        assertThat(result.score()).isLessThan(0);
    }

    @Test
    @DisplayName("detects very positive text")
    void veryPositive() {
        var result = service.analyze("Excellent amazing service! I love it, thank you!");
        assertThat(result.label()).isIn("VERY_POSITIVE", "POSITIVE");
        assertThat(result.score()).isGreaterThan(0);
    }

    @Test
    @DisplayName("handles negation — 'not good' should be negative")
    void negation() {
        var result = service.analyze("The service is not good");
        // 'good' = +1, but 'not' negates → -1
        assertThat(result.score()).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("returns triggers for matched words")
    void triggers() {
        var result = service.analyze("I am angry and frustrated");
        assertThat(result.triggers()).containsAnyOf("angry", "frustrated");
    }

    @Test
    @DisplayName("aggregate sentiment over multiple messages")
    void aggregate() {
        var agg = service.aggregate(List.of(
                "Great service!",
                "Terrible experience",
                "I am happy",
                "This is awful"
        ));
        assertThat(agg.totalScore()).isNotZero();
        assertThat(agg.distribution()).isNotEmpty();
    }

    @Test
    @DisplayName("aggregate handles empty list")
    void aggregateEmpty() {
        var agg = service.aggregate(List.of());
        assertThat(agg.label()).isEqualTo("NEUTRAL");
    }

    @Test
    @DisplayName("handles all-caps and punctuation")
    void formatting() {
        var result = service.analyze("TERRIBLE!!!");
        assertThat(result.score()).isLessThan(0);
    }
}
