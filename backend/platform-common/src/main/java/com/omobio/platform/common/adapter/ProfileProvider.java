package com.omobio.platform.common.adapter;

import java.util.Map;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Canonical interface for customer profile / KYC lookup.
 * Implementations vary per operator / insurer BSS (Dialog, AIA, etc.).
 *
 * <p>PII is never stored in the platform — this provider fetches on demand.
 * MSISDN is masked in logs; NIC / passport is never logged.
 *
 * @see com.omobio.account.service.ProfileService
 */
public interface ProfileProvider extends ApiAdapter {

    // ─── Profile ────────────────────────────────────────────────────────

    /**
     * Retrieve the full customer profile for a connection / policyholder.
     *
     * @param tenantId     the tenant
     * @param connectionId connection ID (telco) or policyholder ID (insurance)
     * @param fields       list of fields to return (empty = all); reduces payload
     * @return             profile record
     */
    ProfileResponse getProfile(
            String tenantId,
            String connectionId,
            List<String> fields
    );

    /**
     * Update mutable profile fields (name, address, language, etc.).
     * Immutable fields (NIC, DOB) must be rejected by the implementation.
     *
     * @param tenantId     the tenant
     * @param connectionId connection / policyholder ID
     * @param updates       field → new value map
     * @return              updated profile
     */
    ProfileResponse updateProfile(
            String tenantId,
            String connectionId,
            Map<String, String> updates
    );

    // ─── KYC ───────────────────────────────────────────────────────────

    /**
     * Initiate or resume a KYC verification flow.
     *
     * @param tenantId     the tenant
     * @param connectionId connection / policyholder ID
     * @param kycType       eKYC level: {@code basic}, {@code advanced}, {@code biometric}
     * @return              KYC session with instructions for the next step
     */
    KycSession initiateKyc(
            String tenantId,
            String connectionId,
            String kycType
    );

    /**
     * Check the current KYC status of a customer.
     *
     * @param tenantId     the tenant
     * @param connectionId connection / policyholder ID
     * @return              KYC status
     */
    KycStatus getKycStatus(
            String tenantId,
            String connectionId
    );

    // ─── Inner types ──────────────────────────────────────────────────

    record ProfileResponse(
            String connectionId,
            String customerName,
            String nic,            // never logged; returned only for display confirmation
            String email,
            String phone,
            String address,
            String language,
            Map<String, Object> extendedFields   // industry-specific additional fields
    ) {}

    enum KycLevel { BASIC, ADVANCED, BIOMETRIC }

    enum KycState { PENDING, IN_PROGRESS, VERIFIED, REJECTED, EXPIRED }

    record KycSession(
            String sessionId,
            KycLevel level,
            KycState state,
            String nextStepUrl,    // URL to continue KYC flow
            long expiresAt         // Unix epoch ms
    ) {}

    record KycStatus(
            String connectionId,
            KycLevel verifiedLevel,
            KycState state,
            String rejectionReason,
            String verifiedAt       // ISO-8601
    ) {}
}
