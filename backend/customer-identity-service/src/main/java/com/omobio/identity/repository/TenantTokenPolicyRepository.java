package com.omobio.identity.repository;

import com.omobio.identity.domain.TenantTokenPolicy;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Mongo repository for {@link TenantTokenPolicy}.
 *
 * Document ID convention: {@code "tenant-token-policy:<tenantId>"}.
 */
@Repository
public interface TenantTokenPolicyRepository extends MongoRepository<TenantTokenPolicy, String> {
}
