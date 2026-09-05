package com.omobio.content.repository;

import com.omobio.content.domain.Banner;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface BannerRepository extends MongoRepository<Banner, String> {

    List<Banner> findByTenantIdAndPositionAndStatusAndActiveFromLessThanEqualAndActiveToGreaterThanEqual(
            String tenantId, String position, String status, Instant from, Instant to);

    List<Banner> findByTenantIdAndStatusAndActiveFromLessThanEqualAndActiveToGreaterThanEqual(
            String tenantId, String status, Instant from, Instant to);

    // ----- admin / management queries -----

    /**
     * List banners by tenant with optional locale filter.
     */
    @org.springframework.data.mongodb.repository.Query(
            value = "{ 'tenantId': ?0, "
                  + "$or: [ { $expr: { $eq: [ ?1, null ] } }, { 'locale': ?1 } ] }",
            sort = "{ 'updatedAt': -1 }")
    List<Banner> findByTenantIdAndOptionalLocale(String tenantId, String locale);
}
