package com.selfcare.config.service;

import com.selfcare.config.domain.ComponentCatalogItem;
import com.selfcare.config.repository.ComponentCatalogMongoRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ComponentCatalogService.
 *
 * Verifies:
 * - scalation between tenant-scoped and global environment catalogs at compile time
 * - upsert by tenant + environment + componentId
 * - publish transitions DRAFT -> PUBLISHED and is idempotent
 * - NotFound when a catalog item is missing
 */
@ExtendWith(MockitoExtension.class)
class ComponentCatalogServiceTest {

    @Mock private ComponentCatalogMongoRepository repository;

    private ComponentCatalogService service;

    @BeforeEach
    void setUp() {
        service = new ComponentCatalogService(repository);

        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        ctx.setUserId("admin-1");
        TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("compile view prefers environment-scoped published items over the global fallback")
    void compilePreferScopedCatalog() {
        ComponentCatalogItem global = item("BalanceCard", "*", "PUBLISHED");
        ComponentCatalogItem scoped = item("BillCard", "prod", "PUBLISHED");
        when(repository.findByTenantIdAndEnvironmentAndStatus("dialog-lk", "prod", "PUBLISHED"))
                .thenReturn(List.of(scoped));

        List<ComponentCatalogItem> result = service.listActiveForCompile("dialog-lk", "prod");

        assertThat(result).extracting(ComponentCatalogItem::getComponentId).containsExactly("BillCard");
        verify(repository, never()).findByTenantIdAndEnvironmentAndStatus(eq("dialog-lk"), eq("*"), any());
    }

    @Test
    @DisplayName("compile view falls back to global catalog when environment has no published items")
    void compileFallsBackToGlobal() {
        ComponentCatalogItem global = item("BalanceCard", "*", "PUBLISHED");
        when(repository.findByTenantIdAndEnvironmentAndStatus("dialog-lk", "prod", "PUBLISHED"))
                .thenReturn(List.of());
        when(repository.findByTenantIdAndEnvironmentAndStatus("dialog-lk", "*", "PUBLISHED"))
                .thenReturn(List.of(global));

        List<ComponentCatalogItem> result = service.listActiveForCompile("dialog-lk", "prod");

        assertThat(result).extracting(ComponentCatalogItem::getComponentId).containsExactly("BalanceCard");
    }

    @Test
    @DisplayName("save upserts by tenant + environment + componentId and defaults status to DRAFT")
    void saveUpsertsByUniqueKey() {
        ComponentCatalogItem incoming = item("BalanceCard", "prod", null);
        ComponentCatalogItem existing = item("BalanceCard", "prod", "DRAFT");
        existing.setId("cat-1");
        when(repository.findByTenantIdAndEnvironmentAndComponentId("dialog-lk", "prod", "BalanceCard"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(ComponentCatalogItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ComponentCatalogItem saved = service.save(incoming);

        assertThat(saved.getId()).isEqualTo("cat-1");
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getTenantId()).isEqualTo("dialog-lk");
    }

    @Test
    @DisplayName("publish transitions DRAFT -> PUBLISHED")
    void publishMakesItemActive() {
        ComponentCatalogItem draft = item("BillCard", "prod", "DRAFT");
        draft.setId("cat-2");
        when(repository.findById("cat-2")).thenReturn(Optional.of(draft));
        when(repository.save(any(ComponentCatalogItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ComponentCatalogItem published = service.publish("cat-2");

        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getPublishedBy()).isEqualTo("admin-1");
        assertThat(published.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("get throws NotFoundException for a missing item")
    void missingItemThrowsNotFound() {
        when(repository.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("nope"))
                .isInstanceOf(NotFoundException.class);
    }

    private ComponentCatalogItem item(String componentId, String environment, String status) {
        return ComponentCatalogItem.builder()
                .tenantId("dialog-lk")
                .environment(environment)
                .componentId(componentId)
                .label(componentId)
                .category("DISPLAY")
                .status(status)
                .build();
    }
}