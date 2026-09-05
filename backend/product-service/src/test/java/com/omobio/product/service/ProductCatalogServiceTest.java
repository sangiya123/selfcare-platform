package com.omobio.product.service;

import com.omobio.product.domain.Product;
import com.omobio.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceTest {

    @Mock private ProductRepository productRepository;

    private ProductCatalogService service;

    @BeforeEach
    void setUp() {
        service = new ProductCatalogService(productRepository);
    }

    @Test
    @DisplayName("listProducts returns active products for tenant")
    void listProducts_activeOnly() {
        Product p1 = Product.builder().id("p1").name("Data Pack 1GB").status("ACTIVE").build();
        Product p2 = Product.builder().id("p2").name("Data Pack 5GB").status("ACTIVE").build();
        Product inactive = Product.builder().id("p3").name("Legacy Pack").status("RETIRED").build();

        when(productRepository.findByTenantIdAndStatus("dialog-lk", "ACTIVE"))
                .thenReturn(List.of(p1, p2));

        List<Product> result = service.listProducts("dialog-lk", "TELCO");

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(Product::getName))
                .containsExactly("Data Pack 1GB", "Data Pack 5GB");
    }

    @Test
    @DisplayName("getProduct returns product when found")
    void getProduct_found() {
        Product product = Product.builder()
                .id("prod-123")
                .name("Monthly Bundle")
                .price(BigDecimal.valueOf(299))
                .currency("LKR")
                .build();

        when(productRepository.findById("prod-123")).thenReturn(Optional.of(product));

        Product result = service.getProduct("prod-123");

        assertThat(result.getName()).isEqualTo("Monthly Bundle");
        assertThat(result.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(299));
    }

    @Test
    @DisplayName("getProduct returns null when not found")
    void getProduct_notFound() {
        when(productRepository.findById("unknown")).thenReturn(Optional.empty());

        Product result = service.getProduct("unknown");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("listProducts filters by industry type")
    void listProducts_filtersByIndustry() {
        Product telcoProduct = Product.builder()
                .id("tp1")
                .name("Prepaid Plan")
                .industryType("TELCO")
                .status("ACTIVE")
                .build();

        when(productRepository.findByTenantIdAndIndustryTypeAndStatus(
                "dialog-lk", "TELCO", "ACTIVE"))
                .thenReturn(List.of(telcoProduct));

        List<Product> result = service.listProducts("dialog-lk", "TELCO");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIndustryType()).isEqualTo("TELCO");
    }
}
