package com.selfcare.account.repository;

import com.selfcare.account.domain.Connection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link Connection} — MySQL InnoDB durable store.
 *
 * Finder methods mirror the access patterns of EntitlementService and ConnectionService:
 * lookup by account, by number (link), by ID, by relationship, and by LOB.
 *
 * Indexes declared on the entity ensure these queries are efficient at scale.
 */
@Repository
public interface ConnectionRepository extends JpaRepository<Connection, String> {

    /**
     * Find all connections for an account by relationship type.
     * Used by EntitlementService.getLinkedConnections() to retrieve
     * all LINKED (non-primary) connections.
     *
     * @param accountId   the account ID
     * @param relationship the relationship (LINKED, PRIMARY)
     * @return list of matching connections
     */
    List<Connection> findByAccountIdAndRelationship(String accountId, String relationship);

    /**
     * Find the primary connection for an account.
     * Used by EntitlementService.getPrimaryConnection() and during account creation.
     *
     * @param accountId the account ID
     * @param isPrimary true to find the primary connection
     * @return the primary connection if found
     */
    Optional<Connection> findByAccountIdAndIsPrimary(String accountId, Boolean isPrimary);

    /**
     * Find a connection by its tenant and MSISDN/number.
     * Used by ConnectionService.getByNumber() during link/unlink operations.
     *
     * @param tenantId the tenant identifier
     * @param number   the connection number/MSISDN
     * @return the connection if found
     */
    Optional<Connection> findByTenantIdAndNumber(String tenantId, String number);

    /**
     * Find all connections for an account (all relationships).
     * Used by ConnectionService.listConnections() for the full list view.
     *
     * @param accountId the account ID
     * @return list of all connections for this account
     */
    List<Connection> findByAccountId(String accountId);

    /**
     * Find all connections for an account with a specific status.
     * Used for filtering by status (ACTIVE, SUSPENDED, etc.).
     *
     * @param accountId the account ID
     * @param status    the connection status
     * @return list of matching connections
     */
    List<Connection> findByAccountIdAndStatus(String accountId, String status);

    /**
     * Find all connections for an account by LOB.
     * Used to filter connections by line of business (MOBILE, BB, DTV, FIBRE).
     *
     * @param accountId the account ID
     * @param lob       the line of business
     * @return list of matching connections
     */
    List<Connection> findByAccountIdAndLob(String accountId, String lob);

    /**
     * Count the number of linked connections for an account.
     * Used to enforce a maximum link limit per account.
     *
     * @param accountId   the account ID
     * @param relationship the relationship type
     * @return count of matching connections
     */
    long countByAccountIdAndRelationship(String accountId, String relationship);

    /**
     * Check whether a connection number already exists for a tenant.
     * Used before linking to prevent duplicate connections.
     *
     * @param tenantId the tenant identifier
     * @param number   the connection number
     * @return true if the number is already registered
     */
    boolean existsByTenantIdAndNumber(String tenantId, String number);

    /**
     * Update the is_primary flag for all connections of an account.
     * Called when switching the primary connection.
     *
     * @param accountId  the account ID
     * @param isPrimary  the new primary flag value
     * @param updatedAt  the update timestamp
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE Connection c SET c.isPrimary = :isPrimary, c.updatedAt = :updatedAt " +
           "WHERE c.accountId = :accountId")
    int resetPrimaryFlag(@Param("accountId") String accountId,
                         @Param("isPrimary") Boolean isPrimary,
                         @Param("updatedAt") Instant updatedAt);

    /**
     * Update the relationship for a connection.
     * Used when linking a connection (set relationship = LINKED)
     * or switching primary (set relationship = PRIMARY).
     *
     * @param connectionId the connection ID
     * @param relationship the new relationship
     * @param updatedAt    the update timestamp
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE Connection c SET c.relationship = :relationship, c.updatedAt = :updatedAt " +
           "WHERE c.connectionId = :connectionId")
    int updateRelationship(@Param("connectionId") String connectionId,
                           @Param("relationship") String relationship,
                           @Param("updatedAt") Instant updatedAt);
}
