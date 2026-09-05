package com.omobio.content.web;

import com.omobio.content.domain.Article;
import com.omobio.content.domain.Banner;
import com.omobio.content.domain.FAQ;
import com.omobio.content.service.ContentService;
import com.omobio.platform.common.dto.PaginationRequest;
import com.omobio.platform.common.dto.PaginationResponse;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Content REST API.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
@Tag(name = "Content", description = "Articles, FAQs, banners")
public class ContentController {

    private final ContentService contentService;

    @GetMapping("/articles")
    @Operation(summary = "List articles")
    public ResponseEntity<ApiResponse<PaginationResponse<Article>>> listArticles(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PaginationResponse<Article> response = contentService.listArticles(
                category, tag, PaginationRequest.builder().page(page).size(size).build());
        return ResponseEntity.ok(ApiResponse.of(response, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/articles/{slug}")
    @Operation(summary = "Get article by slug")
    public ResponseEntity<ApiResponse<Article>> getArticle(@PathVariable String slug) {
        Article article = contentService.getArticle(slug);
        return ResponseEntity.ok(ApiResponse.of(article, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/faqs")
    @Operation(summary = "List FAQs")
    public ResponseEntity<ApiResponse<PaginationResponse<FAQ>>> listFAQs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PaginationResponse<FAQ> response = contentService.listFAQs(category, q, PaginationRequest.builder().page(page).size(size).build());
        return ResponseEntity.ok(ApiResponse.of(response, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/banners")
    @Operation(summary = "Get banners by position")
    public ResponseEntity<ApiResponse<List<Banner>>> getBanners(
            @RequestParam(required = false, defaultValue = "HERO_TOP") String position) {
        List<Banner> banners = contentService.getBanners(position);
        return ResponseEntity.ok(ApiResponse.of(banners, TenantContext.get().getCorrelationId()));
    }
}