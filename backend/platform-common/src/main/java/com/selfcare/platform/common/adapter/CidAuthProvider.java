package com.selfcare.platform.common.adapter;

import java.util.List;

/**
 * CID (Customer Identity) authorization-code provider contract — telco industry.
 *
 * <p>Exchanges a Dialog-style comment-IP authorization code for the customer's
 * federated identity and issues a canonical login result used by
 * {@code customer-identity-service} to mint platform JWT tokens.
 *
 * <p>This mirrors the legacy {@code mda-auth-service} CID flow:
 * <ul>
 *   <li>Exchange {@code authorization_code} for MIFE tokens ({@code dio-token})</li>
 *   <li>Decode the {@code id_token} to recover username, {@code cid_uuid},
 *       display name and header-enrichment flag</li>
 *   <li>Fetch the customer profile (DDS {@code get-by-cid})</li>
 *   <li>Best-effort wallet lookup (DDS {@code get-wallet-by-cid})</li>
 * </ul>
 *
 * <p>Operators implement this interface with their own upstream shapes; all
 * paths, field names and credentials are resolved from DB-backed tenant config.
 */
public interface CidAuthProvider extends ApiAdapter {

    /**
     * Exchange an authorization code for the customer's federated identity.
     *
     * @param tenantId the tenant identifier (e.g. {@code "dialog-lk"})
     * @param request  the authorization code and optional redirect URI
     * @return the canonical exchange result; {@code success == false} on any
     *         upstream failure with a human-readable {@code failureReason}
     */
    CidExchangeResult exchangeCid(String tenantId, CidExchangeRequest request);

    // ================================================================
    // Domain Objects
    // ================================================================

    /**
     * Request to exchange an authorization code.
     *
     * @param code        the authorization code issued after the user logs in
     * @param redirectUri optional redirect URI; operators fall back to a
     *                    tenant-configured default when absent
     */
    record CidExchangeRequest(String code, String redirectUri) {}

    /**
     * Canonical result of a CID exchange.
     *
     * <p>Values are taken from the operator's {@code id_token} claims and
     * profile API response. Identity fields are kept in a reusable form so
     * the consuming service can store the session and enrich the JWT without
     * knowing the upstream API shape.
     *
     * @param success         true when the full chain (token + profile) succeeded
     * @param failureReason   human-readable reason when {@code success} is false
     * @param username        login / primary MSISDN from the id_token
     * @param cidUuid         federated identity UUID ({@code sub} claim)
     * @param cxName          display name (first + last name)
     * @param headerEnriched  true when the id_token {@code amr} includes {@code hdr}
     * @param mifeAccessToken operator access token (kept for the session/refresh path)
     * @param mifeRefreshToken operator refresh token for MIFE token rotation
     * @param mifeIdToken     the raw encrypted id_token returned by the operator
     * @param mifeExpiresIn   operator access-token TTL in seconds, if provided
     * @param profileId       customer profile document id
     * @param profileVersion  customer profile version
     * @param profileImageUrl customer profile image URL, if any
     * @param walletId        customer wallet id, when the profile has one
     * @param connections     the customer's linked connections (entitlement)
     */
    record CidExchangeResult(
            boolean success,
            String failureReason,
            String username,
            String cidUuid,
            String cxName,
            boolean headerEnriched,
            String mifeAccessToken,
            String mifeRefreshToken,
            String mifeIdToken,
            Integer mifeExpiresIn,
            String profileId,
            String profileVersion,
            String profileImageUrl,
            String walletId,
            List<ConnectionDetail> connections
    ) {}

    /**
     * A single linked connection from the customer profile.
     *
     * <p>Canonical (camelCase) field set covers the telco selfcare contract;
     * operators map their API field names in DB-backed {@code fieldMapping}.
     */
    record ConnectionDetail(
            String number,
            String name,
            String connectionId,
            String contractId,
            String lob,
            String connectionType,
            String identificationType,
            String identificationNumber,
            boolean isDialog,
            boolean isPrimary,
            String createdTime,
            String updatedTime,
            boolean isHybrid,
            String cvType,
            boolean underPrimaryNic,
            String careOfNic,
            boolean careOfNicValidated,
            boolean isCorporate,
            String cvStatus
    ) {}
}