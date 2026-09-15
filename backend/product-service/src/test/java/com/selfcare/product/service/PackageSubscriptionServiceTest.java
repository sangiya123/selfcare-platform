package com.selfcare.product.service;

import com.selfcare.platform.common.adapter.ActivationProvider;
import com.selfcare.platform.common.adapter.EligibilityProvider;
import com.selfcare.platform.common.web.NotFoundException;
import com.selfcare.product.domain.Product;
import com.selfcare.product.service.PackageSubscriptionService.PackageOffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies the canonical package journey (browse / detail / active / eligibility /
 * activate / deactivate). Provider resolution is by adapterId (never by tenant if),
 * and external BSS calls are delegated to the operator's ActivationProvider /
 * EligibilityProvider with graceful degradation on failure.
 */
@ExtendWith(MockitoExtension.class)
class PackageSubscriptionServiceTest {

    @Mock private ProductQueryService queryService;

    private ActivationProvider dialogActivation;
    private EligibilityProvider dialogEligibility;
    private PackageSubscriptionService service;

    private Product dataPack;

    @BeforeEach
    void setUp() {
        dialogActivation = mock(ActivationProvider.class);
        lenient().when(dialogActivation.getAdapterId()).thenReturn("dialog-lk");
        dialogEligibility = mock(EligibilityProvider.class);
        lenient().when(dialogEligibility.getAdapterId()).thenReturn("dialog-lk");

        service = new PackageSubscriptionService(
                queryService, List.of(dialogActivation), List.of(dialogEligibility));

        dataPack = Product.builder()
                .productId("prod-1")
                .tenantId("dialog-lk")
                .sourceProductId("D1GB")
                .sourceSystem("DIALOG_BSS")
                .name("1GB Anytime")
                .description("1GB any-network data")
                .category("DATA_PACK")
                .subcategory("DAILY")
                .lob("DATA")
                .connectionType("prepaid")
                .price(BigDecimal.valueOf(60))
                .currency("LKR")
                .validityDays(1)
                .allowances("1GB")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("browse enriches catalog with eligibility and active state from the tenant provider")
    void browse_enrichesOffer() {
        when(queryService.listActiveProducts("dialog-lk", "DATA")).thenReturn(List.of(dataPack));
        when(dialogEligibility.getEligibleProducts("dialog-lk", "0771234567"))
                .thenReturn(List.of(new EligibilityProvider.EligibleProduct(
                        "D1GB", "1GB Anytime", "DATA", true, "OFFER-1",
                        Instant.parse("2026-12-31T23:59:59Z"), Map.of("blockers", "none"))));
        when(dialogActivation.getActivePackages("dialog-lk", "0771234567"))
                .thenReturn(List.of(new ActivationProvider.ActivePackage(
                        "apg-1", "0771234567", "D1GB", "1GB Anytime",
                        Instant.parse("2026-09-01T00:00:00Z"), null, "ACTIVE", true)));

        List<PackageOffer> offers = service.browsePackages("dialog-lk", null, "DATA", "0771234567");

        assertThat(offers).hasSize(1);
        PackageOffer offer = offers.get(0);
        assertThat(offer.productCode()).isEqualTo("D1GB");
        assertThat(offer.sourceSystem()).isEqualTo("DIALOG_BSS");
        assertThat(offer.price()).isEqualByComparingTo(BigDecimal.valueOf(60));
        assertThat(offer.currency()).isEqualTo("LKR");
        assertThat(offer.eligible()).isTrue();
        assertThat(offer.offerCode()).isEqualTo("OFFER-1");
        assertThat(offer.active()).isTrue();
        assertThat(offer.activeStatus()).isEqualTo("ACTIVE");
        verify(dialogEligibility).getEligibleProducts("dialog-lk", "0771234567");
        verify(dialogActivation).getActivePackages("dialog-lk", "0771234567");
    }

    @Test
    @DisplayName("browse filters by category")
    void browse_filtersByCategory() {
        Product voicePack = Product.builder()
                .productId("prod-2")
                .tenantId("dialog-lk")
                .sourceProductId("D200MIN")
                .name("200min On-net")
                .category("VOICE_PACK")
                .lob("VOICE")
                .status("ACTIVE")
                .build();
        when(queryService.listActiveProducts("dialog-lk", null)).thenReturn(List.of(dataPack, voicePack));

        List<PackageOffer> offers = service.browsePackages("dialog-lk", "DATA_PACK", null, null);

        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).productCode()).isEqualTo("D1GB");
    }

    @Test
    @DisplayName("browse skips provider calls when no connection is supplied")
    void browse_noConnectionSkipsProviders() {
        when(queryService.listActiveProducts("dialog-lk", "DATA")).thenReturn(List.of(dataPack));

        List<PackageOffer> offers = service.browsePackages("dialog-lk", null, "DATA", null);

        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).eligible()).isFalse();
        assertThat(offers.get(0).active()).isFalse();
        verifyNoInteractions(dialogEligibility, dialogActivation);
    }

    @Test
    @DisplayName("browse degrades gracefully when the eligibility provider fails")
    void browse_degradesOnEligibilityFailure() {
        when(queryService.listActiveProducts("dialog-lk", "DATA")).thenReturn(List.of(dataPack));
        when(dialogEligibility.getEligibleProducts("dialog-lk", "0771234567"))
                .thenThrow(new RuntimeException("BSS unreachable"));
        when(dialogActivation.getActivePackages("dialog-lk", "0771234567")).thenReturn(List.of());

        List<PackageOffer> offers = service.browsePackages("dialog-lk", null, "DATA", "0771234567");

        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).eligible()).isFalse();
        assertThat(offers.get(0).active()).isFalse();
    }

    @Test
    @DisplayName("activate forwards the operator product code to the matched tenant provider")
    void activate_delegatesWithSourceProductId() {
        dataPack.setSourceProductId("D1GB");
        ActivationProvider.ActivationResult result = new ActivationProvider.ActivationResult(
                true, "act-1", "0771234567", "D1GB",
                ActivationProvider.ActivationStatusCode.SUCCESS,
                Instant.parse("2026-09-01T00:00:00Z"), null, null);

        when(queryService.getProduct("dialog-lk", "prod-1")).thenReturn(dataPack);
        when(dialogActivation.activate("dialog-lk", "0771234567", "D1GB")).thenReturn(result);

        ActivationProvider.ActivationResult actual =
                service.activate("dialog-lk", "0771234567", "prod-1");

        assertThat(actual).isEqualTo(result);
        verify(dialogActivation).activate("dialog-lk", "0771234567", "D1GB");
    }

    @Test
    @DisplayName("getActivePackages returns the tenant provider's active list")
    void getActivePackages_usesMatchedProvider() {
        List<ActivationProvider.ActivePackage> packages = List.of(
                new ActivationProvider.ActivePackage(
                        "apg-2", "0771234567", "D1GB", "1GB Anytime",
                        Instant.parse("2026-09-01T00:00:00Z"), null, "ACTIVE", false));
        when(dialogActivation.getActivePackages("dialog-lk", "0771234567")).thenReturn(packages);

        List<ActivationProvider.ActivePackage> actual =
                service.getActivePackages("dialog-lk", "0771234567");

        assertThat(actual).isEqualTo(packages);
    }

    @Test
    @DisplayName("throws NotFoundException when no provider is registered for the tenant")
    void activate_noProviderThrows() {
        ActivationProvider hutchActivation = mock(ActivationProvider.class);
        when(hutchActivation.getAdapterId()).thenReturn("hutch-lk");
        PackageSubscriptionService isolated = new PackageSubscriptionService(
                queryService, List.of(hutchActivation), List.of());

        when(queryService.getProduct("dialog-lk", "prod-1")).thenReturn(dataPack);

        assertThatThrownBy(() -> isolated.activate("dialog-lk", "0771234567", "prod-1"))
                .isInstanceOf(NotFoundException.class);
        verify(hutchActivation, never()).activate(anyString(), anyString(), anyString());
    }
}