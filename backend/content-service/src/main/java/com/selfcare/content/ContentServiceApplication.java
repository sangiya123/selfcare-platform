package com.selfcare.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Content Service — CMS, articles, FAQs, banners.
 *
 * Capabilities:
 * - Articles (news, promotions, how-tos)
 * - FAQs (categorized, searchable)
 * - Banners (carousel, hero)
 * - Legal content (terms, privacy)
 * - Multi-locale (en, si, ta, etc.)
 * - Versioning and publish workflow
 * - Cache for fast reads
 *
 * Stored in MongoDB (flexible content model).
 */
@SpringBootApplication
@EnableMongoRepositories(basePackages = {"com.selfcare.content.repository", "com.selfcare.platform.common.config"})
@ComponentScan(basePackages = {
    "com.selfcare.content",
    "com.selfcare.platform.common"
})
public class ContentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}