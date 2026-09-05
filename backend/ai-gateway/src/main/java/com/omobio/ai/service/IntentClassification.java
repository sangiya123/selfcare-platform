package com.omobio.ai.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Intent classification result for a user message.
 *
 * Classifies the customer's intent based on their message.
 * Used by the AI gateway to route to the right action, suggest replies,
 * and prioritize response tone.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentClassification {

    /**
     * Enumerated intent types recognized by the platform.
     */
    public enum Intent {
        /** Customer wants to know their current balance. */
        BALANCE_INQUIRY,
        /** Customer wants to recharge/top-up their account. */
        RECHARGE_HELP,
        /** Customer is lodging a complaint or issue. */
        COMPLAINT,
        /** Customer is asking a frequently asked question. */
        FAQ,
        /** Customer is inquiring about a bill or payment. */
        BILL_INQUIRY,
        /** Customer wants to know their usage (data, voice, SMS). */
        USAGE_INQUIRY,
        /** Customer wants to change their plan or package. */
        PLAN_CHANGE,
        /** Customer wants to add a bundle or add-on. */
        BUNDLE_PURCHASE,
        /** Customer is greeting or making small talk. */
        GREETING,
        /** General conversation that doesn't fit a specific category. */
        GENERAL,
        /** The intent could not be determined confidently. */
        UNKNOWN
    }

    /** Classified intent. */
    private Intent intent;

    /** Confidence score (0.0–1.0). */
    private double confidence;

    /**
     * Suggested action to take based on the intent.
     * This is the AI-suggested next step (e.g., "check_balance", "show_plans").
     */
    private String suggestedAction;

    /**
     * Entities extracted from the message (e.g., specific plan names, dates).
     */
    private java.util.Map<String, String> extractedEntities;

    /**
     * Whether the confidence is high enough to auto-act.
     * If false, the system should prompt the customer for clarification.
     */
    private boolean autoActEligible;

    /**
     * Create an UNKNOWN classification with the given confidence.
     */
    public static IntentClassification unknown(double confidence) {
        return IntentClassification.builder()
                .intent(Intent.UNKNOWN)
                .confidence(confidence)
                .suggestedAction("HAND_OFF_AGENT")
                .autoActEligible(false)
                .build();
    }
}
