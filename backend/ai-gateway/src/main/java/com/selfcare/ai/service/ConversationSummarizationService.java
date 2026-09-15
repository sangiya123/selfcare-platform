package com.selfcare.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Conversation Summarization Service — produces structured summaries of chat sessions.
 *
 * Extracts:
 *   - Key topics discussed
 *   - Customer intent
 *   - Action items / tool calls
 *   - Sentiment
 *   - Resolution status
 *   - Suggested follow-ups
 *
 * Implementation: extractive summarization (TF-IDF on key sentences + rule-based
 * classification). Production: pluggable to an LLM-based summarizer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummarizationService {

    private static final int MAX_KEY_POINTS = 5;
    private static final Set<String> RESOLUTION_POSITIVE = Set.of(
            "resolved", "fixed", "solved", "done", "complete", "thank you", "thanks"
    );
    private static final Set<String> RESOLUTION_NEGATIVE = Set.of(
            "still", "not working", "issue persists", "didn't help", "worse", "complaint"
    );
    private static final Set<String> FOLLOWUP_TRIGGERS = Set.of(
            "when", "how long", "follow up", "later", "next", "callback", "escalate"
    );

    private final SentimentAnalysisService sentimentService;

    /**
     * Summarize a conversation history.
     *
     * @param messages list of messages (alternating user/assistant)
     * @return structured summary
     */
    public Summary summarize(List<ChatRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new Summary("", "NO_CONVERSATION", List.of(), "NEUTRAL", "PENDING",
                    List.of(), 0, 0);
        }

        // 1. Extract topics via keyword frequency
        List<String> topics = extractTopics(messages);

        // 2. Classify the dominant intent
        String dominantIntent = classifyIntent(messages);

        // 3. Detect action items (tool calls + explicit requests)
        List<String> actionItems = extractActionItems(messages);

        // 4. Aggregate sentiment
        List<String> userMessages = messages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .map(ChatRequest.Message::getContent)
                .filter(Objects::nonNull)
                .toList();
        SentimentAnalysisService.AggregateSentiment sentiment = sentimentService.aggregate(userMessages);

        // 5. Resolution status
        String resolutionStatus = detectResolution(messages);

        // 6. Suggested follow-ups
        List<String> followUps = generateFollowUps(topics, resolutionStatus, actionItems);

        // 7. Headline summary — pick the most important sentence
        String headline = generateHeadline(messages, topics);

        int userCount = userMessages.size();
        int assistantCount = (int) messages.stream()
                .filter(m -> "assistant".equals(m.getRole()))
                .count();

        return new Summary(headline, dominantIntent, topics,
                sentiment.label(), resolutionStatus, followUps, userCount, assistantCount);
    }

    // -------------------------------------------------------------------------
    // Implementation
    // -------------------------------------------------------------------------

    private List<String> extractTopics(List<ChatRequest.Message> messages) {
        Map<String, Integer> freq = new HashMap<>();
        Set<String> stopWords = Set.of(
                "the", "is", "a", "an", "and", "or", "but", "i", "you", "to", "of",
                "in", "on", "for", "with", "at", "by", "from", "this", "that", "it",
                "be", "have", "has", "had", "do", "does", "did", "will", "would", "can",
                "could", "should", "may", "might", "my", "your", "we", "us", "they",
                "their", "he", "she", "as", "if", "not", "no", "yes"
        );

        for (ChatRequest.Message m : messages) {
            if (m.getContent() == null || !"user".equals(m.getRole())) continue;
            String[] words = m.getContent().toLowerCase().replaceAll("[^a-zA-Z\\s]", "").split("\\s+");
            for (String word : words) {
                if (word.length() > 3 && !stopWords.contains(word)) {
                    freq.merge(word, 1, Integer::sum);
                }
            }
        }

        return freq.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(MAX_KEY_POINTS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String classifyIntent(List<ChatRequest.Message> messages) {
        for (ChatRequest.Message m : messages) {
            if (m.getContent() == null || !"user".equals(m.getRole())) continue;
            String text = m.getContent().toLowerCase();
            if (text.contains("balance") || text.contains("how much")) return "BALANCE_INQUIRY";
            if (text.contains("recharge") || text.contains("top up")) return "RECHARGE";
            if (text.contains("bill") || text.contains("invoice")) return "BILL_INQUIRY";
            if (text.contains("plan") || text.contains("package")) return "PLAN_CHANGE";
            if (text.contains("complaint") || text.contains("issue") || text.contains("problem"))
                return "SUPPORT";
            if (text.contains("claim") || text.contains("policy") || text.contains("premium"))
                return "INSURANCE";
        }
        return "GENERAL";
    }

    private List<String> extractActionItems(List<ChatRequest.Message> messages) {
        List<String> items = new ArrayList<>();
        for (ChatRequest.Message m : messages) {
            if (m.getToolCall() != null) {
                items.add("Tool called: " + m.getToolCall().getName());
            }
        }
        return items;
    }

    private String detectResolution(List<ChatRequest.Message> messages) {
        // Look at the last few messages for resolution signals
        int start = Math.max(0, messages.size() - 6);
        for (int i = start; i < messages.size(); i++) {
            String text = messages.get(i).getContent();
            if (text == null) continue;
            String lower = text.toLowerCase();
            for (String sig : RESOLUTION_NEGATIVE) {
                if (lower.contains(sig)) return "UNRESOLVED";
            }
        }
        for (int i = start; i < messages.size(); i++) {
            String text = messages.get(i).getContent();
            if (text == null) continue;
            String lower = text.toLowerCase();
            for (String sig : RESOLUTION_POSITIVE) {
                if (lower.contains(sig)) return "RESOLVED";
            }
        }
        return "PENDING";
    }

    private List<String> generateFollowUps(List<String> topics, String resolutionStatus,
                                           List<String> actionItems) {
        List<String> followUps = new ArrayList<>();
        for (String item : actionItems) {
            followUps.add("Follow up on " + item + " completion");
        }
        if ("UNRESOLVED".equals(resolutionStatus)) {
            followUps.add("Schedule a callback with a human agent");
            followUps.add("Send a written summary to the customer's email");
        }
        for (String topic : topics) {
            if (topic.contains("bill") || topic.contains("invoice")) {
                followUps.add("Follow up on billing dispute resolution in 24h");
            }
            if (topic.contains("plan") || topic.contains("package")) {
                followUps.add("Confirm plan activation after recharge");
            }
        }
        return followUps.stream().distinct().limit(3).toList();
    }

    private String generateHeadline(List<ChatRequest.Message> messages, List<String> topics) {
        // Find the first user message
        for (ChatRequest.Message m : messages) {
            if ("user".equals(m.getRole()) && m.getContent() != null) {
                String text = m.getContent();
                return text.length() > 100 ? text.substring(0, 97) + "..." : text;
            }
        }
        return "Conversation about " + String.join(", ", topics);
    }

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    public record Summary(
            String headline,
            String dominantIntent,
            List<String> topics,
            String sentiment,
            String resolutionStatus,
            List<String> suggestedFollowUps,
            int userMessageCount,
            int assistantMessageCount
    ) {}
}
