package com.selfcare.content.repository;

import com.selfcare.content.domain.FAQ;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FAQRepository extends MongoRepository<FAQ, String> {

    Page<FAQ> findByTenantIdAndStatusOrderByDisplayOrderAsc(
            String tenantId, String status, Pageable pageable);

    Page<FAQ> findByTenantIdAndCategoryAndStatusOrderByDisplayOrderAsc(
            String tenantId, String category, String status, Pageable pageable);

    @Query("{ 'tenantId': ?0, 'status': ?1, $or: [ "
         + "{ 'translations.en.question': { $regex: ?2, $options: 'i' } }, "
         + "{ 'translations.en.answer': { $regex: ?2, $options: 'i' } }, "
         + "{ 'translations.si.question': { $regex: ?2, $options: 'i' } }, "
         + "{ 'translations.ta.question': { $regex: ?2, $options: 'i' } }, "
         + "{ 'tags': { $regex: ?2, $options: 'i' } } ] }")
    Page<FAQ> search(String tenantId, String searchTerm, Pageable pageable);

    @Query(value = "{ 'tenantId': ?0, 'status': 'ACTIVE' }", sort = "{ 'displayOrder': 1 }")
    List<FAQ> findActive(String tenantId, Pageable pageable);

    // ----- admin / management queries -----

    /**
     * Find FAQs by tenant with optional locale / category / status filter.
     */
    @Query(value = "{ 'tenantId': ?0, "
         + "$and: [ "
         + "  { $or: [ { $expr: { $eq: [ ?1, null ] } }, { 'locale': ?1 } ] }, "
         + "  { $or: [ { $expr: { $eq: [ ?2, null ] } }, { 'category': ?2 } ] }, "
         + "  { $or: [ { $expr: { $eq: [ ?3, null ] } }, { 'status': ?3 } ] } "
         + "] }", sort = "{ 'displayOrder': 1 }")
    List<FAQ> findByTenantIdAndOptionalLocaleAndCategoryAndStatus(
            String tenantId, String locale, String category, String status);
}
