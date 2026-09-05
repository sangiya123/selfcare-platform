package com.omobio.identity.repository;

import com.omobio.identity.domain.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link OtpCode}.
 *
 * <p>Indexes on (tenantId, identifier) and (correlationId) support the two
 * primary lookup paths during OTP verification.
 *
 * <p>The expiry-cleanup query is deliberately broad: it targets ACTIVE codes
 * that have passed their expiresAt and marks them EXPIRED before they are ever
 * matched by a verify query.
 */
@Repository
public interface OtpRepository extends JpaRepository<OtpCode, String> {

    /**
     * Find the most recently issued active OTP for a given tenant+identifier.
     * Used during verify to retrieve the latest code for a customer.
     *
     * @param tenantId   the tenant ID
     * @param identifier the MSISDN / email / customer ID
     * @param status     expected status (OtpCode.STATUS_ACTIVE)
     * @return the most recent active OTP, if any
     */
    @Query("SELECT o FROM OtpCode o WHERE o.tenantId = :tenantId " +
           "AND o.identifier = :identifier AND o.status = :status " +
           "ORDER BY o.createdAt DESC LIMIT 1")
    Optional<OtpCode> findLatestActiveByTenantAndIdentifier(
            @Param("tenantId") String tenantId,
            @Param("identifier") String identifier,
            @Param("status") String status);

    /**
     * Find an OTP by its correlation ID.
     * The correlation ID is set on generate and echoed back to the client;
     * it can be used as an alternative lookup key on verify.
     *
     * @param correlationId the correlation ID from the generate response
     * @return the OTP record
     */
    Optional<OtpCode> findByCorrelationId(String correlationId);

    /**
     * Find all active OTPs for a tenant+identifier (used for resend, cancel).
     *
     * @param tenantId   the tenant ID
     * @param identifier the identifier
     * @param status     expected status
     * @return list of matching OTPs
     */
    List<OtpCode> findByTenantIdAndIdentifierAndStatus(
            String tenantId, String identifier, String status);

    /**
     * Mark all active OTPs for a tenant+identifier as expired.
     * Called before issuing a new OTP to prevent a burst of codes for the same
     * identifier.
     *
     * @param tenantId   the tenant ID
     * @param identifier the identifier
     * @param status     status to match (OtpCode.STATUS_ACTIVE)
     */
    @Modifying
    @Query("UPDATE OtpCode o SET o.status = 'EXPIRED' " +
           "WHERE o.tenantId = :tenantId AND o.identifier = :identifier " +
           "AND o.status = :status")
    int expireActiveByTenantAndIdentifier(
            @Param("tenantId") String tenantId,
            @Param("identifier") String identifier,
            @Param("status") String status);

    /**
     * Batch-cleanup of expired-but-not-yet-marked OTPs.
     * Run as a scheduled task or before high-traffic windows.
     *
     * @param now current instant
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE OtpCode o SET o.status = 'EXPIRED' " +
           "WHERE o.status = 'ACTIVE' AND o.expiresAt < :now")
    int expireStaleOtps(@Param("now") Instant now);

    /**
     * Count active OTPs for a tenant+identifier — used to rate-limit OTP requests.
     *
     * @param tenantId   the tenant ID
     * @param identifier  the identifier
     * @param status     expected status
     * @return count of active OTPs
     */
    long countByTenantIdAndIdentifierAndStatus(
            String tenantId, String identifier, String status);
}
