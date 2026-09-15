package com.selfcare.product.service;

import com.selfcare.platform.common.web.NotFoundException;
import com.selfcare.product.domain.Product;
import com.selfcare.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the catalog read-side query service: active product listing
 * (optionally lob-filtered) and single-product lookup.
 */
@ExtendWith(MockitoExtension.class)
class ProductQueryServiceTest {

    @Mock private ProductRepository productRepository;

    private ProductQueryService service;

    @BeforeEach
    void setUp() {
        service = new ProductQueryService(productRepository);
    }

    @Test
    @DisplayName("listActiveProducts returns active products for tenant in display order")
    void listActiveProducts_noLob() {
        Product p1 = Product.builder().productId("p1").name("1GB Anytime").lob("DATA").status("ACTIVE").build();
        Product p2 = Product.builder().productId("p2").name("200min On-net").lob("VOICE").status("ACTIVE").build();

        when(productRepository.findByTenantIdAndStatusOrderByDisplayOrderAsc("dialog-lk", "ACTIVE"))
                .thenReturn(List.of(p1, p2));

        List<Product> result = service.listActiveProducts("dialog-lk", null);

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(Product::getProductId)).containsExactly("p1", "p2");
        verify(productRepository, never()).findByTenantIdAndStatusAndLobOrderByDisplayOrderAsc(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("listActiveProducts filters by lob when provided")
    void listActiveProducts_withLob() {
        Product dataPack = Product.builder().productId("p1").name("1GB Anytime").lob("DATA").status("ACTIVE").build();

        when(productRepository.findByTenantIdAndStatusAndLobOrderByDisplayOrderAsc("dialog-lk", "ACTIVE", "DATA"))
                .thenReturn(List.of(dataPack));

        List<Product> result = service.listActiveProducts("dialog-lk", "data");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductId()).isEqualTo("p1");
    }

    @Test
    @DisplayName("getProduct throws NotFoundException when missing")
    void getProduct_notFound() {
        when(productRepository.findByTenantIdAndProductId("dialog-lk", "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProduct("dialog-lk", "nope"))
                .isInstanceOf(NotFoundException.class);
    }
}