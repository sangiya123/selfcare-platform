package com.omobio.content.service;

import com.omobio.content.domain.Article;
import com.omobio.content.domain.Banner;
import com.omobio.content.domain.FAQ;
import com.omobio.content.repository.ArticleRepository;
import com.omobio.content.repository.BannerRepository;
import com.omobio.content.repository.FAQRepository;
import com.omobio.platform.common.dto.PaginationRequest;
import com.omobio.platform.common.dto.PaginationResponse;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ContentService.
 *
 * Verifies:
 * - listArticles (with and without cache)
 * - getArticle (by slug)
 * - listFAQs (with search)
 * - getBanners (active, by position)
 * - Admin create/update/delete (cache invalidation)
 *
 * Uses Redis cache mocking with TTL of 5 minutes.
 */
@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

    @Mock private ArticleRepository articleRepository;
    @Mock private FAQRepository faqRepository;
    @Mock private BannerRepository bannerRepository;
    @Mock private RedisTemplate<String, Object> cache;
    @Mock private ValueOperations<String, Object> valueOps;

    private ContentService service;

    @BeforeEach
    void setUp() {
        when(cache.opsForValue()).thenReturn(valueOps);
        service = new ContentService(articleRepository, faqRepository, bannerRepository, cache);

        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ======================================================================
    // listArticles
    // ======================================================================

    @Test
    @DisplayName("listArticles returns cached response when available")
    void listArticles_cacheHit() {
        @SuppressWarnings("unchecked")
        PaginationResponse<Article> cached = new PaginationResponse<>(
                List.of(Article.builder().id("art-1").slug("test").build()),
                1, 0, 10);
        when(valueOps.get(contains("content:articles"))).thenReturn(cached);

        PaginationResponse<Article> result = service.listArticles(
                "news", null, PaginationRequest.builder().page(0).size(10).build());

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getId()).isEqualTo("art-1");
        // No DB call
        verifyNoInteractions(articleRepository);
    }

    @Test
    @DisplayName("listArticles queries DB on cache miss")
    void listArticles_cacheMiss() {
        when(valueOps.get(anyString())).thenReturn(null);

        Article article = Article.builder()
                .id("art-1")
                .tenantId("dialog-lk")
                .slug("getting-started")
                .category("help")
                .build();
        Page<Article> page = new PageImpl<>(List.of(article));
        when(articleRepository.findByTenantIdAndCategoryAndStatusAndPublishedAtLessThanEqual(
                eq("dialog-lk"), eq("help"), eq("PUBLISHED"), any(Instant.class), any(Pageable.class)))
                .thenReturn(page);

        PaginationResponse<Article> result = service.listArticles(
                "help", null, PaginationRequest.builder().page(0).size(10).build());

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getSlug()).isEqualTo("getting-started");
        // Cache should be populated
        verify(valueOps).set(contains("content:articles"), any(), any());
    }

    @Test
    @DisplayName("listArticles returns empty page when nothing published")
    void listArticles_empty() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(articleRepository.findByTenantIdAndCategoryAndStatusAndPublishedAtLessThanEqual(
                anyString(), anyString(), anyString(), any(Instant.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        PaginationResponse<Article> result = service.listArticles(
                "news", null, PaginationRequest.builder().page(0).size(10).build());

        assertThat(result.getItems()).isEmpty();
    }

    // ======================================================================
    // getArticle
    // ======================================================================

    @Test
    @DisplayName("getArticle returns published article by slug")
    void getArticle_found() {
        Article article = Article.builder()
                .id("art-1")
                .tenantId("dialog-lk")
                .slug("pricing-guide")
                .status("PUBLISHED")
                .build();
        when(articleRepository.findByTenantIdAndSlug("dialog-lk", "pricing-guide"))
                .thenReturn(Optional.of(article));

        Article result = service.getArticle("pricing-guide");

        assertThat(result.getSlug()).isEqualTo("pricing-guide");
    }

    @Test
    @DisplayName("getArticle throws NotFoundException for unknown slug")
    void getArticle_notFound() {
        when(articleRepository.findByTenantIdAndSlug("dialog-lk", "does-not-exist"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getArticle("does-not-exist"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("pricing-guide");
    }

    // ======================================================================
    // listFAQs
    // ======================================================================

    @Test
    @DisplayName("listFAQs returns active FAQs for category")
    void listFAQs_found() {
        FAQ faq = FAQ.builder()
                .id("faq-1")
                .tenantId("dialog-lk")
                .category("billing")
                .build();
        when(faqRepository.findByTenantIdAndCategoryAndStatusOrderByDisplayOrderAsc(
                "dialog-lk", "billing", "ACTIVE"))
                .thenReturn(List.of(faq));

        List<FAQ> result = service.listFAQs("billing", null);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("listFAQs returns all active FAQs when no category specified")
    void listFAQs_noCategory() {
        FAQ faq1 = FAQ.builder().id("faq-1").build();
        FAQ faq2 = FAQ.builder().id("faq-2").build();
        when(faqRepository.findByTenantIdAndStatusOrderByDisplayOrderAsc("dialog-lk", "ACTIVE"))
                .thenReturn(List.of(faq1, faq2));

        List<FAQ> result = service.listFAQs(null, null);

        assertThat(result).hasSize(2);
    }

    // ======================================================================
    // getBanners
    // ======================================================================

    @Test
    @DisplayName("getBanners returns active banners by position")
    void getBanners_found() {
        Banner banner = Banner.builder()
                .id("banner-1")
                .tenantId("dialog-lk")
                .position("HERO_TOP")
                .status("ACTIVE")
                .build();
        when(bannerRepository.findByTenantIdAndPositionAndStatusAndActiveFromLessThanEqualAndActiveToGreaterThanEqual(
                "dialog-lk", "HERO_TOP", "ACTIVE", any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(banner));

        List<Banner> result = service.getBanners("HERO_TOP");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPosition()).isEqualTo("HERO_TOP");
    }

    // ======================================================================
    // Admin CRUD — cache invalidation
    // ======================================================================

    @Test
    @DisplayName("saveArticle invalidates articles cache")
    void saveArticle_invalidatesCache() {
        Article article = Article.builder()
                .id("art-2")
                .slug("new-post")
                .tenantId("dialog-lk")
                .status("PUBLISHED")
                .build();
        when(articleRepository.save(any(Article.class))).thenReturn(article);

        service.saveArticle(article);

        verify(cache).delete(contains("content:articles"));
    }

    @Test
    @DisplayName("deleteArticle invalidates articles cache")
    void deleteArticle_invalidatesCache() {
        Article article = Article.builder()
                .id("art-3")
                .tenantId("dialog-lk")
                .build();
        when(articleRepository.findById("art-3")).thenReturn(Optional.of(article));

        service.deleteArticle("art-3");

        verify(cache).delete(contains("content:articles"));
    }

    @Test
    @DisplayName("saveFAQ invalidates FAQs cache")
    void saveFAQ_invalidatesCache() {
        FAQ faq = FAQ.builder().id("faq-new").build();
        when(faqRepository.save(any(FAQ.class))).thenReturn(faq);

        service.saveFAQ(faq);

        verify(cache).delete(contains("content:faqs"));
    }

    @Test
    @DisplayName("saveBanner invalidates banners cache")
    void saveBanner_invalidatesCache() {
        Banner banner = Banner.builder().id("banner-new").build();
        when(bannerRepository.save(any(Banner.class))).thenReturn(banner);

        service.saveBanner(banner);

        verify(cache).delete(contains("content:banners"));
    }
}
