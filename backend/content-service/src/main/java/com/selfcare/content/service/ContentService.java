package com.selfcare.content.service;

import com.selfcare.content.domain.Article;
import com.selfcare.content.domain.Banner;
import com.selfcare.content.domain.FAQ;
import com.selfcare.content.repository.ArticleRepository;
import com.selfcare.content.repository.BannerRepository;
import com.selfcare.content.repository.FAQRepository;
import com.selfcare.platform.common.dto.PaginationRequest;
import com.selfcare.platform.common.dto.PaginationResponse;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Content query service.
 *
 * Caches content in Redis with short TTL (5 minutes) for fast reads.
 * Admin publishes invalidate cache.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    private final ArticleRepository articleRepository;
    private final FAQRepository faqRepository;
    private final BannerRepository bannerRepository;
    private final RedisTemplate<String, Object> cache;

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    // ============================================================
    // Articles
    // ============================================================

    public PaginationResponse<Article> listArticles(
            String category, String tag, PaginationRequest pagination) {
        String tenantId = TenantContext.get().getTenantId();
        String cacheKey = "selfcare:content:articles:" + tenantId + ":" + category + ":" + tag + ":" + pagination.getPage();
        Object cached = cache.opsForValue().get(cacheKey);
        if (cached instanceof PaginationResponse) {
            return (PaginationResponse<Article>) cached;
        }

        Pageable pageReq = PageRequest.of(pagination.getPage(), pagination.getSize(),
                Sort.by(Sort.Direction.DESC, "publishedAt"));
        Instant now = Instant.now();
        Page<Article> page;

        if (tag != null && !tag.isBlank()) {
            page = articleRepository.findByTenantIdAndTagsContaining(tenantId, tag, pageReq);
        } else if (category != null && !category.isBlank()) {
            page = articleRepository.findByTenantIdAndCategoryAndStatusAndPublishedAtLessThanEqual(
                    tenantId, category, "PUBLISHED", now, pageReq);
        } else {
            page = articleRepository.findByTenantIdAndStatusAndPublishedAtLessThanEqual(
                    tenantId, "PUBLISHED", now, pageReq);
        }

        PaginationResponse<Article> response = PaginationResponse.<Article>builder()
                .items(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();

        cache.opsForValue().set(cacheKey, response, CACHE_TTL);
        return response;
    }

    public Article getArticle(String slug) {
        String tenantId = TenantContext.get().getTenantId();
        String cacheKey = "selfcare:content:article:" + tenantId + ":" + slug;
        Object cached = cache.opsForValue().get(cacheKey);
        if (cached instanceof Article) {
            return (Article) cached;
        }

        Article article = articleRepository.findByTenantIdAndSlug(tenantId, slug)
                .orElseThrow(() -> new NotFoundException("Article", slug));

        cache.opsForValue().set(cacheKey, article, CACHE_TTL);
        return article;
    }

    // ============================================================
    // FAQs
    // ============================================================

    public PaginationResponse<FAQ> listFAQs(String category, String search, PaginationRequest pagination) {
        String tenantId = TenantContext.get().getTenantId();
        Page<FAQ> page;
        Pageable pageReq = PageRequest.of(pagination.getPage(), pagination.getSize(),
                Sort.by(Sort.Direction.ASC, "displayOrder"));
        if (search != null && !search.isEmpty()) {
            page = faqRepository.search(tenantId, search, pageReq);
        } else if (category != null && !category.isEmpty()) {
            page = faqRepository.findByTenantIdAndCategoryAndStatusOrderByDisplayOrderAsc(
                    tenantId, category, "ACTIVE", pageReq);
        } else {
            page = faqRepository.findByTenantIdAndStatusOrderByDisplayOrderAsc(tenantId, "ACTIVE", pageReq);
        }
        return PaginationResponse.<FAQ>builder()
                .items(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    // ============================================================
    // Banners
    // ============================================================

    public List<Banner> getBanners(String position) {
        String tenantId = TenantContext.get().getTenantId();
        String cacheKey = "selfcare:content:banners:" + tenantId + ":" + position;
        Object cached = cache.opsForValue().get(cacheKey);
        if (cached instanceof List) {
            return (List<Banner>) cached;
        }

        Instant now = Instant.now();
        List<Banner> banners = position != null
                ? bannerRepository.findByTenantIdAndPositionAndStatusAndActiveFromLessThanEqualAndActiveToGreaterThanEqual(
                        tenantId, position, "ACTIVE", now, now)
                : bannerRepository.findByTenantIdAndStatusAndActiveFromLessThanEqualAndActiveToGreaterThanEqual(
                        tenantId, "ACTIVE", now, now);

        cache.opsForValue().set(cacheKey, banners, CACHE_TTL);
        return banners;
    }

    // ============================================================
    // Admin CRUD (used by AdminContentController)
    // ============================================================

    /**
     * List all articles for a tenant (no status filter), admin-only.
     */
    public List<Article> listArticlesByTenant(String tenantId, String locale, String status) {
        return articleRepository.findByTenantIdAndOptionalLocaleAndStatus(
                tenantId, locale, status);
    }

    /**
     * Get article by ID, scoped to tenant.
     */
    public java.util.Optional<Article> getArticleById(String id) {
        return articleRepository.findById(id);
    }

    /**
     * Save or update an article.
     */
    @org.springframework.transaction.annotation.Transactional
    public Article saveArticle(Article article) {
        Article saved = articleRepository.save(article);
        invalidateArticleCache(saved.getTenantId());
        return saved;
    }

    /**
     * Delete article, scoped to tenant.
     */
    @org.springframework.transaction.annotation.Transactional
    public void deleteArticle(String id, String tenantId) {
        articleRepository.findById(id).ifPresent(a -> {
            if (tenantId.equals(a.getTenantId())) {
                articleRepository.delete(a);
                invalidateArticleCache(tenantId);
            }
        });
    }

    /**
     * List FAQs by tenant, with optional locale, category, status.
     */
    public List<FAQ> listFaqsByTenant(String tenantId, String locale, String category, String status) {
        return faqRepository.findByTenantIdAndOptionalLocaleAndCategoryAndStatus(
                tenantId, locale, category, status);
    }

    /**
     * Get FAQ by ID.
     */
    public java.util.Optional<FAQ> getFaqById(String id) {
        return faqRepository.findById(id);
    }

    /**
     * Save or update a FAQ.
     */
    @org.springframework.transaction.annotation.Transactional
    public FAQ saveFaq(FAQ faq) {
        FAQ saved = faqRepository.save(faq);
        invalidateFaqCache(saved.getTenantId());
        return saved;
    }

    /**
     * Delete FAQ.
     */
    @org.springframework.transaction.annotation.Transactional
    public void deleteFaq(String id, String tenantId) {
        faqRepository.findById(id).ifPresent(f -> {
            if (tenantId.equals(f.getTenantId())) {
                faqRepository.delete(f);
                invalidateFaqCache(tenantId);
            }
        });
    }

    /**
     * List banners by tenant.
     */
    public List<Banner> listBannersByTenant(String tenantId, String locale) {
        return bannerRepository.findByTenantIdAndOptionalLocale(tenantId, locale);
    }

    /**
     * Get banner by ID.
     */
    public java.util.Optional<Banner> getBannerById(String id) {
        return bannerRepository.findById(id);
    }

    /**
     * Save or update a banner.
     */
    @org.springframework.transaction.annotation.Transactional
    public Banner saveBanner(Banner banner) {
        Banner saved = bannerRepository.save(banner);
        invalidateBannerCache(saved.getTenantId());
        return saved;
    }

    /**
     * Delete banner.
     */
    @org.springframework.transaction.annotation.Transactional
    public void deleteBanner(String id, String tenantId) {
        bannerRepository.findById(id).ifPresent(b -> {
            if (tenantId.equals(b.getTenantId())) {
                bannerRepository.delete(b);
                invalidateBannerCache(tenantId);
            }
        });
    }

    // ----- Cache invalidation -----

    private void invalidateArticleCache(String tenantId) {
        try {
            var keys = cache.keys("selfcare:content:article:" + tenantId + ":*");
            if (keys != null && !keys.isEmpty()) cache.delete(keys);
            var pageKeys = cache.keys("selfcare:content:articles:" + tenantId + ":*");
            if (pageKeys != null && !pageKeys.isEmpty()) cache.delete(pageKeys);
        } catch (Exception e) {
            log.warn("Failed to invalidate article cache: {}", e.getMessage());
        }
    }

    private void invalidateFaqCache(String tenantId) {
        try {
            var keys = cache.keys("selfcare:content:faqs:" + tenantId + ":*");
            if (keys != null && !keys.isEmpty()) cache.delete(keys);
        } catch (Exception e) {
            log.warn("Failed to invalidate FAQ cache: {}", e.getMessage());
        }
    }

    private void invalidateBannerCache(String tenantId) {
        try {
            var keys = cache.keys("selfcare:content:banners:" + tenantId + ":*");
            if (keys != null && !keys.isEmpty()) cache.delete(keys);
        } catch (Exception e) {
            log.warn("Failed to invalidate banner cache: {}", e.getMessage());
        }
    }
}
