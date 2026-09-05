package com.omobio.account.repository;

import com.omobio.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link Account} — MySQL InnoDB durable store.
 *
 * Finder methods mirror the access patterns of AccountService:
 * lookup by primary identity (login), by tenant (list), and by ID (operations).
 *
 * Indexes declared on the entity ensure these queries are efficient at scale.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    /**
     * Find an account by its tenant and primary identity.
     * Used during login to look up the account for a given MSISDN/customer ID.
     *
     * @param tenantId       the tenant identifier
     * @param primaryIdentity the primary identity (MSISDN for telco)
     * @return the account if found
     */
    Optional<Account> findByTenantIdAndPrimaryIdentity(String tenantId, String primaryIdentity);

    /**
     * Find all accounts for a tenant.
     * Used by admin listing.
     *
     * @param tenantId the tenant identifier
     * @return list of accounts belonging to this tenant
     */
    List<Account> findByTenantId(String tenantId);

    /**
     * Check whether an account exists by tenant and primary identity.
     * Used before creating to avoid duplicate accounts.
     *
     * @param tenantId       the tenant identifier
     * @param primaryIdentity the primary identity
     * @return true if an account already exists
     */
    boolean existsByTenantIdAndPrimaryIdentity(String tenantId, String primaryIdentity);

    /**
     * Update the last login timestamp for an account.
     *
     * @param accountId the account ID
     * @param loginAt   the login timestamp
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE Account a SET a.lastLoginAt = :loginAt WHERE a.accountId = :accountId")
    int updateLastLoginAt(@Param("accountId") String accountId, @Param("loginAt") Instant loginAt);

    /**
     * Update the profile version for an account.
     * Called when a Kafka profile-updated event is received.
     *
     * @param accountId       the account ID
     * @param profileVersion  the new profile version from source system
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE Account a SET a.profileVersion = :profileVersion WHERE a.accountId = :accountId")
    int updateProfileVersion(@Param("accountId") String accountId, @Param("profileVersion") Long profileVersion);

    /**
     * Count accounts per tenant (useful for analytics).
     *
     * @param tenantId the tenant identifier
     * @return count of accounts
     */
    long countByTenantId(String tenantId);
}
