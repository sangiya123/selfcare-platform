package com.selfcare.product.service;

import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.product.domain.Offer;
import com.selfcare.product.domain.Product;
import com.selfcare.product.repository.OfferRepository;
import com.selfcare.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Personalized offer/product recommendations.
 *
 * The recommendation score combines several signals:
 *  - offer.priority (admin-controlled)
 *  - connection-type match
 *  - segment match (NEW / LOYAL / POSTPAID / PREPAID / ...)
 *  - usage-based boost: customers running low on a category get
 *    offers that bundle more of that category
 *  - recency boost: recently created offers get a small boost
 *
 * The AI service may later override these base recommendations with
 * LLM-generated personalized suggestions, but the deterministic
 * rules are the source of truth for the home-screen widget.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final OfferRepository offerRepository;
    private final ProductRepository productRepository;

    @Value("${product.recommendation.default-limit:5}")
    private int defaultLimit;

    @Transactional(readOnly = true)
    public List<Recommendation> recommend(
            String connectionType,
            String segment,
            Map<String, Long> usageByCategory) {

        String tenantId = TenantContext.get().getTenantId();
        Instant now = Instant.now();
        String seg = segment != null ? segment : "ALL";
        String ct = connectionType != null ? connectionType : "ALL";

        List<Offer> offers = offerRepository.findActiveForSegment(tenantId, seg, ct);
        Map<String, Product> productMap = productRepository
                .findAllById(offers.stream().map(Offer::getProductId).toList())
                .stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));

        return offers.stream()
                .map(o -> toRecommendation(o, productMap.get(o.getProductId()), usageByCategory, now))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(Recommendation::getScore).reversed())
                .limit(defaultLimit)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Recommendation> activeOffers() {
        String tenantId = TenantContext.get().getTenantId();
        Instant now = Instant.now();
        List<Offer> offers = offerRepository.findActiveAsOf(tenantId, now);
        Map<String, Product> productMap = productRepository
                .findAllById(offers.stream().map(Offer::getProductId).toList())
                .stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));
        return offers.stream()
                .map(o -> toRecommendation(o, productMap.get(o.getProductId()), Map.of(), now))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(Recommendation::getPriority))
                .limit(defaultLimit)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Offer> activeOffersForProduct(String productId) {
        String tenantId = TenantContext.get().getTenantId();
        return offerRepository.findActiveOffersForProduct(tenantId, productId, Instant.now());
    }

    private Recommendation toRecommendation(Offer offer, Product product,
                                            Map<String, Long> usageByCategory, Instant now) {
        if (offer == null || product == null) return null;

        double score = 100.0;
        // Lower priority number = higher score
        score += Math.max(0, 200 - offer.getPriority());
        // Boost when product category matches high-usage category
        if (product.getCategory() != null && usageByCategory != null) {
            Long used = usageByCategory.get(product.getCategory().toUpperCase());
            if (used != null && used > 0) {
                // Higher usage = more likely to run out = higher need = higher score
                score += Math.min(50.0, Math.log10(Math.max(1, used)) * 10);
            }
        }
        // Recency boost (max 10 points for offers created in the last 7 days)
        if (offer.getCreatedAt() != null) {
            long daysOld = (now.toEpochMilli() - offer.getCreatedAt().toEpochMilli()) / 86_400_000L;
            if (daysOld >= 0 && daysOld <= 7) {
                score += (7 - daysOld) * 1.4;
            }
        }

        return Recommendation.builder()
                .offerId(offer.getOfferId())
                .productId(product.getProductId())
                .name(offer.getName() != null ? offer.getName() : product.getName())
                .description(offer.getDescription() != null ? offer.getDescription() : product.getDescription())
                .category(product.getCategory())
                .offerType(offer.getOfferType())
                .value(offer.getValue())
                .currency(offer.getCurrency() != null ? offer.getCurrency() : product.getCurrency())
                .priority(offer.getPriority())
                .score(score)
                .build();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Recommendation {
        private String offerId;
        private String productId;
        private String name;
        private String description;
        private String category;
        private String offerType;
        private java.math.BigDecimal value;
        private String currency;
        private Integer priority;
        private Double score;
    }
}
