package com.omobio.content.web;

import com.omobio.content.domain.Article;
import com.omobio.content.domain.Banner;
import com.omobio.content.domain.FAQ;
import com.omobio.content.service.ContentService;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import com.omobio.platform.common.web.BadRequestException;
import com.omobio.platform.common.web.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Admin CRUD API for CMS content.
 *
 * Endpoints:
 *   GET    /                              List all content items
 *   GET    /articles                     List articles
 *   POST   /articles                     Create article
 *   PUT    /articles/{id}               Update article
 *   DELETE /articles/{id}                Delete article
 *   GET    /faqs                         List FAQs
 *   POST   /faqs                         Create FAQ
 *   PUT    /faqs/{id}                   Update FAQ
 *   DELETE /faqs/{id}                    Delete FAQ
 *   GET    /banners                      List banners
 *   POST   /banners                      Create banner
 *   PUT    /banners/{id}                Update banner
 *   DELETE /banners/{id}                Delete banner
 *   GET    /legal                        List legal texts
 *   POST   /legal                        Create legal text
 *   PUT    /legal/{id}                  Update legal text
 *   GET    /templates                    List templates
 *   POST   /templates                    Create template
 *
 * All content supports multi-locale via the locale parameter.
 * Content follows the Draft → Published workflow.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/content")
@RequiredArgsConstructor
@Tag(name = "Admin Content", description = "Admin CRUD for CMS: articles, FAQs, banners, legal, templates")
public class AdminContentController {

    private final ContentService contentService;

    // -----------------------------------------------------------------
    // Articles
    // -----------------------------------------------------------------

    @GetMapping("/articles")
    @Operation(summary = "List articles")
    public ResponseEntity<ApiResponse<List<Article>>> listArticles(
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) String status) {
        List<Article> articles = contentService.listArticlesByTenant(
                TenantContext.get().getTenantId(), locale, status);
        return ResponseEntity.ok(ApiResponse.of(articles));
    }

    @PostMapping("/articles")
    @Operation(summary = "Create article")
    public ResponseEntity<ApiResponse<Article>> createArticle(@RequestBody Article article) {
        article.setId(null);
        article.setTenantId(TenantContext.get().getTenantId());
        article.setCreatedAt(Instant.now());
        article.setUpdatedAt(Instant.now());
        Article saved = contentService.saveArticle(article);
        log.info("Article created: id={}, tenant={}, by={}",
                saved.getId(), saved.getTenantId(), TenantContext.get().getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(saved));
    }

    @PutMapping("/articles/{id}")
    @Operation(summary = "Update article")
    public ResponseEntity<ApiResponse<Article>> updateArticle(
            @PathVariable String id, @RequestBody Article input) {
        Article existing = contentService.getArticleById(id)
                .orElseThrow(() -> new NotFoundException("Article", id));
        if (!existing.getTenantId().equals(TenantContext.get().getTenantId())) {
            throw new BadRequestException("Article belongs to a different tenant");
        }
        input.setId(id);
        input.setTenantId(existing.getTenantId());
        input.setCreatedAt(existing.getCreatedAt());
        input.setUpdatedAt(Instant.now());
        Article saved = contentService.saveArticle(input);
        log.info("Article updated: id={}, tenant={}, by={}",
                id, saved.getTenantId(), TenantContext.get().getUserId());
        return ResponseEntity.ok(ApiResponse.of(saved));
    }

    @DeleteMapping("/articles/{id}")
    @Operation(summary = "Delete article")
    public ResponseEntity<ApiResponse<Void>> deleteArticle(@PathVariable String id) {
        contentService.deleteArticle(id, TenantContext.get().getTenantId());
        log.info("Article deleted: id={}, tenant={}, by={}",
                id, TenantContext.get().getTenantId(), TenantContext.get().getUserId());
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    // -----------------------------------------------------------------
    // FAQs
    // -----------------------------------------------------------------

    @GetMapping("/faqs")
    @Operation(summary = "List FAQs")
    public ResponseEntity<ApiResponse<List<FAQ>>> listFaqs(
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        List<FAQ> faqs = contentService.listFaqsByTenant(
                TenantContext.get().getTenantId(), locale, category, status);
        return ResponseEntity.ok(ApiResponse.of(faqs));
    }

    @PostMapping("/faqs")
    @Operation(summary = "Create FAQ")
    public ResponseEntity<ApiResponse<FAQ>> createFaq(@RequestBody FAQ faq) {
        faq.setId(null);
        faq.setTenantId(TenantContext.get().getTenantId());
        faq.setCreatedAt(Instant.now());
        faq.setUpdatedAt(Instant.now());
        FAQ saved = contentService.saveFaq(faq);
        log.info("FAQ created: id={}, tenant={}, by={}",
                saved.getId(), saved.getTenantId(), TenantContext.get().getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(saved));
    }

    @PutMapping("/faqs/{id}")
    @Operation(summary = "Update FAQ")
    public ResponseEntity<ApiResponse<FAQ>> updateFaq(@PathVariable String id, @RequestBody FAQ input) {
        FAQ existing = contentService.getFaqById(id)
                .orElseThrow(() -> new NotFoundException("FAQ", id));
        if (!existing.getTenantId().equals(TenantContext.get().getTenantId())) {
            throw new BadRequestException("FAQ belongs to a different tenant");
        }
        input.setId(id);
        input.setTenantId(existing.getTenantId());
        input.setCreatedAt(existing.getCreatedAt());
        input.setUpdatedAt(Instant.now());
        FAQ saved = contentService.saveFaq(input);
        log.info("FAQ updated: id={}, tenant={}, by={}",
                id, saved.getTenantId(), TenantContext.get().getUserId());
        return ResponseEntity.ok(ApiResponse.of(saved));
    }

    @DeleteMapping("/faqs/{id}")
    @Operation(summary = "Delete FAQ")
    public ResponseEntity<ApiResponse<Void>> deleteFaq(@PathVariable String id) {
        contentService.deleteFaq(id, TenantContext.get().getTenantId());
        log.info("FAQ deleted: id={}, tenant={}, by={}",
                id, TenantContext.get().getTenantId(), TenantContext.get().getUserId());
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    // -----------------------------------------------------------------
    // Banners
    // -----------------------------------------------------------------

    @GetMapping("/banners")
    @Operation(summary = "List banners")
    public ResponseEntity<ApiResponse<List<Banner>>> listBanners(
            @RequestParam(required = false) String locale) {
        List<Banner> banners = contentService.listBannersByTenant(
                TenantContext.get().getTenantId(), locale);
        return ResponseEntity.ok(ApiResponse.of(banners));
    }

    @PostMapping("/banners")
    @Operation(summary = "Create banner")
    public ResponseEntity<ApiResponse<Banner>> createBanner(@RequestBody Banner banner) {
        banner.setId(null);
        banner.setTenantId(TenantContext.get().getTenantId());
        banner.setCreatedAt(Instant.now());
        banner.setUpdatedAt(Instant.now());
        Banner saved = contentService.saveBanner(banner);
        log.info("Banner created: id={}, tenant={}, by={}",
                saved.getId(), saved.getTenantId(), TenantContext.get().getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(saved));
    }

    @PutMapping("/banners/{id}")
    @Operation(summary = "Update banner")
    public ResponseEntity<ApiResponse<Banner>> updateBanner(
            @PathVariable String id, @RequestBody Banner input) {
        Banner existing = contentService.getBannerById(id)
                .orElseThrow(() -> new NotFoundException("Banner", id));
        if (!existing.getTenantId().equals(TenantContext.get().getTenantId())) {
            throw new BadRequestException("Banner belongs to a different tenant");
        }
        input.setId(id);
        input.setTenantId(existing.getTenantId());
        input.setCreatedAt(existing.getCreatedAt());
        input.setUpdatedAt(Instant.now());
        Banner saved = contentService.saveBanner(input);
        log.info("Banner updated: id={}, tenant={}, by={}",
                id, saved.getTenantId(), TenantContext.get().getUserId());
        return ResponseEntity.ok(ApiResponse.of(saved));
    }

    @DeleteMapping("/banners/{id}")
    @Operation(summary = "Delete banner")
    public ResponseEntity<ApiResponse<Void>> deleteBanner(@PathVariable String id) {
        contentService.deleteBanner(id, TenantContext.get().getTenantId());
        log.info("Banner deleted: id={}, tenant={}, by={}",
                id, TenantContext.get().getTenantId(), TenantContext.get().getUserId());
        return ResponseEntity.ok(ApiResponse.of(null));
    }
}
