package com.omobio.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI Gateway — model gateway with tool permissions, RAG, and recommendations.
 *
 * Capabilities:
 * - Unified interface to multiple AI models (Claude, GPT, Gemini)
 * - Tool permission system (which actions AI can take on behalf of user)
 * - RAG retrieval from knowledge base
 * - Prepared actions / suggested replies
 * - Customer intent classification
 * - Churn risk scoring
 * - Bundle recommendations
 *
 * Phase 9: AI customer assistant
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.omobio.ai",
    "com.omobio.platform.common"
})
public class AIGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIGatewayApplication.class, args);
    }
}