package com.omobio.content.repository;

import com.omobio.content.domain.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface ArticleRepository extends MongoRepository<Article, String> {

    Optional<Article> findByTenantIdAndSlug(String tenantId, String slug);

    Page<Article> findByTenantIdAndStatusAndPublishedAtLessThanEqual(
            String tenantId, String status, Instant publishedAt, Pageable pageable);

    Page<Article> findByTenantIdAndCategoryAndStatusAndPublishedAtLessThanEqual(
            String tenantId, String category, String status, Instant publishedAt, Pageable pageable);

    Page<Article> findByTenantIdAndTagsContaining(
            String tenantId, String tag, Pageable pageable);

    @Query("{ 'tenantId': ?0, 'status': ?1, $or: [ { 'publishAt': null }, { 'publishAt': { $lte: ?2 } } ], "
         + "$or: [ { 'unpublishAt': null }, { 'unpublishAt': { $gt: ?2 } } ] }")
    Page<Article> findPublished(String tenantId, String status, Instant asOf, Pageable pageable);

    @Query(value = "{ 'tenantId': ?0, 'tags': ?1 }", sort = "{ 'publishedAt': -1 }")
    java.util.List<Article> findByTag(String tenantId, String tag, Pageable pageable);

    @Query(value = "{ 'tenantId': ?0, 'category': ?1 }", sort = "{ 'publishedAt': -1 }")
    java.util.List<Article> findByCategory(String tenantId, String category, Pageable pageable);

    // ----- admin / management queries -----

    /**
     * Find all articles for a tenant with optional locale/status filter.
     * Used by admin list view — no pagination at the query level (service
     * applies limit).
     */
    @Query(value = "{ 'tenantId': ?0, "
         + "$and: [ "
         + "  { $or: [ { $expr: { $eq: [ ?1, null ] } }, { 'locale': ?1 } ] }, "
         + "  { $or: [ { $expr: { $eq: [ ?2, null ] } }, { 'status': ?2 } ] } "
         + "] }", sort = "{ 'updatedAt': -1 }")
    java.util.List<Article> findByTenantIdAndOptionalLocaleAndStatus(
            String tenantId, String locale, String status);
}
