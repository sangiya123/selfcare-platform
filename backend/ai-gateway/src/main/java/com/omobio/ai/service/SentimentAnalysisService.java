package com.omobio.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Sentiment Analysis Service — detects the emotional tone of customer messages.
 *
 * Sentiment values:
 *   - VERY_NEGATIVE (-2)
 *   - NEGATIVE (-1)
 *   - NEUTRAL (0)
 *   - POSITIVE (1)
 *   - VERY_POSITIVE (2)
 *
 * Used by:
 *   - Dashboard BFF: alert admins if a customer is highly frustrated
 *   - Admin reporting: aggregate sentiment trends per tenant
 *   - Chat: detect escalation triggers
 *
 * Implementation: rule-based lexicon (production: ML model).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentAnalysisService {

    private static final Map<String, Integer> SENTIMENT_LEXICON = new HashMap<>();

    static {
        // Strongly negative
        SENTIMENT_LEXICON.put("angry", -2);
        SENTIMENT_LEXICON.put("furious", -2);
        SENTIMENT_LEXICON.put("terrible", -2);
        SENTIMENT_LEXICON.put("awful", -2);
        SENTIMENT_LEXICON.put("horrible", -2);
        SENTIMENT_LEXICON.put("scam", -2);
        SENTIMENT_LEXICON.put("fraud", -2);
        SENTIMENT_LEXICON.put("worst", -2);
        SENTIMENT_LEXICON.put("hate", -2);
        SENTIMENT_LEXICON.put("sue", -2);
        SENTIMENT_LEXICON.put("lawsuit", -2);

        // Negative
        SENTIMENT_LEXICON.put("bad", -1);
        SENTIMENT_LEXICON.put("poor", -1);
        SENTIMENT_LEXICON.put("disappointed", -1);
        SENTIMENT_LEXICON.put("frustrated", -1);
        SENTIMENT_LEXICON.put("annoyed", -1);
        SENTIMENT_LEXICON.put("broken", -1);
        SENTIMENT_LEXICON.put("slow", -1);
        SENTIMENT_LEXICON.put("expensive", -1);
        SENTIMENT_LEXICON.put("issue", -1);
        SENTIMENT_LEXICON.put("problem", -1);
        SENTIMENT_LEXICON.put("complaint", -1);
        SENTIMENT_LEXICON.put("refund", -1);
        SENTIMENT_LEXICON.put("wrong", -1);
        SENTIMENT_LEXICON.put("unhappy", -1);

        // Positive
        SENTIMENT_LEXICON.put("good", 1);
        SENTIMENT_LEXICON.put("nice", 1);
        SENTIMENT_LEXICON.put("thanks", 1);
        SENTIMENT_LEXICON.put("thank", 1);
        SENTIMENT_LEXICON.put("helpful", 1);
        SENTIMENT_LEXICON.put("happy", 1);
        SENTIMENT_LEXICON.put("satisfied", 1);
        SENTIMENT_LEXICON.put("great", 1);
        SENTIMENT_LEXICON.put("appreciate", 1);

        // Strongly positive
        SENTIMENT_LEXICON.put("excellent", 2);
        SENTIMENT_LEXICON.put("amazing", 2);
        SENTIMENT_LEXICON.put("love", 2);
        SENTIMENT_LEXICON.put("perfect", 2);
        SENTIMENT_LEXICON.put("fantastic", 2);
        SENTIMENT_LEXICON.put("brilliant", 2);
        SENTIMENT_LEXICON.put("outstanding", 2);
    }

    private static final Set<String> NEGATION_WORDS = Set.of(
            "not", "no", "never", "without", "none", "nothing", "neither"
    );

    /**
     * Analyze sentiment of a single message.
     */
    public SentimentResult analyze(String text) {
        if (text == null || text.isBlank()) {
            return new SentimentResult(0, "NEUTRAL", 0.0, List.of());
        }

        String[] words = text.toLowerCase().split("\\s+");
        int score = 0;
        int matches = 0;
        List<String> triggers = new ArrayList<>();

        for (int i = 0; i < words.length; i++) {
            String word = words[i].replaceAll("[^a-z]", "");
            Integer s = SENTIMENT_LEXICON.get(word);
            if (s != null) {
                // Check for negation in previous 2 words
                boolean negated = false;
                for (int j = Math.max(0, i - 2); j < i; j++) {
                    if (NEGATION_WORDS.contains(words[j])) {
                        negated = true;
                        break;
                    }
                }
                score += negated ? -s : s;
                matches++;
                if (Math.abs(s) > 0) {
                    triggers.add(word);
                }
            }
        }

        SentimentLabel label = scoreToLabel(score);
        double confidence = matches == 0 ? 0.1 : Math.min(1.0, matches / 5.0);

        return new SentimentResult(score, label.name(), confidence, triggers);
    }

    /**
     * Aggregate sentiment over a list of messages.
     */
    public AggregateSentiment aggregate(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return new AggregateSentiment(0, "NEUTRAL", 0.0, Map.of());
        }

        int total = 0;
        Map<SentimentLabel, Integer> dist = new EnumMap<>(SentimentLabel.class);
        for (SentimentLabel lbl : SentimentLabel.values()) dist.put(lbl, 0);

        for (String m : messages) {
            SentimentResult r = analyze(m);
            total += r.score();
            dist.merge(SentimentLabel.valueOf(r.label()), 1, Integer::sum);
        }

        double avg = (double) total / messages.size();
        return new AggregateSentiment(total, scoreToLabel(total).name(), avg,
                dist.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        e -> e.getKey().name(),
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                )));
    }

    private SentimentLabel scoreToLabel(int score) {
        if (score <= -3) return SentimentLabel.VERY_NEGATIVE;
        if (score < 0) return SentimentLabel.NEGATIVE;
        if (score == 0) return SentimentLabel.NEUTRAL;
        if (score < 3) return SentimentLabel.POSITIVE;
        return SentimentLabel.VERY_POSITIVE;
    }

    // -------------------------------------------------------------------------
    // Result types
    // -------------------------------------------------------------------------

    public enum SentimentLabel {
        VERY_NEGATIVE, NEGATIVE, NEUTRAL, POSITIVE, VERY_POSITIVE
    }

    public record SentimentResult(
            int score,
            String label,
            double confidence,
            List<String> triggers
    ) {}

    public record AggregateSentiment(
            int totalScore,
            String label,
            double average,
            Map<String, Integer> distribution
    ) {}
}
