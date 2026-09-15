package com.selfcare.product.service;

import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.product.domain.Offer;
import com.selfcare.product.domain.Product;
import com.selfcare.product.repository.OfferRepository;
import com.selfcare.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private OfferRepository offerRepository;
    @Mock private ProductRepository productRepository;

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(offerRepository, productRepository);
        ReflectionTestUtils.setField(service, "defaultLimit", 5);
        TenantContext.current().setTenantId("t1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("recommend returns sorted recommendations, boosting usage-matching category")
    void recommend_boostsUsageMatch() {
        Offer o1 = offer("o-1", "DATA", 50);
        Offer o2 = offer("o-2", "VOICE", 80);
        Product p1 = product("p-data", "DATA");
        Product p2 = product("p-voice", "VOICE");

        when(offerRepository.findActiveForSegment("t1", "ALL", "ALL"))
                .thenReturn(List.of(o1, o2));
        when(productRepository.findAllById(any()))
                .thenReturn(List.of(p1, p2));

        List<RecommendationService.Recommendation> result = service.recommend(
                "ALL", "ALL", Map.of("DATA", 1_000_000_000L));

        assertThat(result).hasSize(2);
        // DATA offer should outrank VOICE because usage matches
        assertThat(result.get(0).getCategory()).isEqualTo("DATA");
        assertThat(result.get(1).getCategory()).isEqualTo("VOICE");
    }

    @Test
    @DisplayName("recommend filters out offers without a matching product")
    void recommend_skipsOffersWithoutProduct() {
        Offer o1 = offer("o-1", "DATA", 50);
        when(offerRepository.findActiveForSegment("t1", "ALL", "ALL"))
                .thenReturn(List.of(o1));
        when(productRepository.findAllById(any())).thenReturn(List.of()); // no products

        List<RecommendationService.Recommendation> result = service.recommend(
                "ALL", "ALL", Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("activeOffers returns offers ordered by priority ASC")
    void activeOffers_orderByPriority() {
        Offer o1 = offer("o-1", "DATA", 200);
        Offer o2 = offer("o-2", "VOICE", 50);
        Product p1 = product("p-data", "DATA");
        Product p2 = product("p-voice", "VOICE");
        when(offerRepository.findActiveAsOf(eq("t1"), any())).thenReturn(List.of(o1, o2));
        when(productRepository.findAllById(any())).thenReturn(List.of(p1, p2));

        List<RecommendationService.Recommendation> result = service.activeOffers();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPriority()).isEqualTo(50);
        assertThat(result.get(1).getPriority()).isEqualTo(200);
    }

    @Test
    @DisplayName("activeOffersForProduct returns active offers for given product")
    void activeOffersForProduct() {
        Offer o = offer("o-1", "DATA", 100);
        when(offerRepository.findActiveOffersForProduct(eq("t1"), eq("p-1"), any(Instant.class)))
                .thenReturn(List.of(o));

        List<Offer> result = service.activeOffersForProduct("p-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOfferId()).isEqualTo("o-1");
    }

    @Test
    @DisplayName("isCurrentlyActive returns true for ACTIVE within window")
    void offerIsActive() {
        Offer active = offer("o-1", "DATA", 100);
        active.setStatus("ACTIVE");
        active.setValidFrom(Instant.now().minusSeconds(3600));
        active.setValidUntil(Instant.now().plusSeconds(3600));
        assertThat(active.isCurrentlyActive()).isTrue();

        Offer expired = offer("o-2", "DATA", 100);
        expired.setStatus("ACTIVE");
        expired.setValidFrom(Instant.now().minusSeconds(86400));
        expired.setValidUntil(Instant.now().minusSeconds(3600));
        assertThat(expired.isCurrentlyActive()).isFalse();
    }

    private Offer offer(String id, String productCategory, int priority) {
        return Offer.builder()
                .offerId(id)
                .tenantId("t1")
                .productId("p-" + productCategory.toLowerCase())
                .name("Offer " + id)
                .offerType("DISCOUNT")
                .value(new BigDecimal("10.0"))
                .currency("LKR")
                .targetSegment("ALL")
                .eligibleConnectionType("ALL")
                .status("ACTIVE")
                .priority(priority)
                .createdAt(Instant.now())
                .build();
    }

    private Product product(String id, String category) {
        return Product.builder()
                .productId(id)
                .tenantId("t1")
                .name(category + " Plan")
                .category(category)
                .status("ACTIVE")
                .currency("LKR")
                .build();
    }
}
