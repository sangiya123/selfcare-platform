package com.selfcare.platform.common.adapter;

import java.time.Instant;
import java.util.List;

/**
 * Activation Provider contract for telco industry packs.
 *
 * Each operator (Dialog, Hutch, Airtel, ...) implements this interface to
 * expose canonical package / VAS (Value-Added Service) activation capabilities
 * required by the product-service and BFFs:
 *
 * <ul>
 *   <li>Activate a package, add-on, or VAS for a connection</li>
 *   <li>Deactivate / remove an active package or VAS</li>
 *   <li>List currently active packages / add-ons for a connection</li>
 *   <li>Query activation status</li>
 * </ul>
 *
 * Note: ActivationProvider is distinct from {@link com.selfcare.platform.common.adapter.ProductCatalogProvider}
 * which returns the catalog of available products. ActivationProvider mutates
 * the subscriber's entitlements on the BSS.
 *
 * All methods take {@code tenantId} as the first argument.
 */
public interface ActivationProvider extends ApiAdapter {

    /**
     * Activate a package, add-on or VAS for a connection.
     *
     * @param tenantId the tenant identifier (e.g. {@code "dialog-lk"})
     * @param connectionId the connection ID (MSISDN)
     * @param productCode the operator-specific product code to activate
     * @return activation result
     */
    ActivationResult activate(String tenantId, String connectionId, String productCode);

    /**
     * Deactivate an active package, add-on or VAS.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @param productCode the operator-specific product code to deactivate
     * @return deactivation result
     */
    ActivationResult deactivate(String tenantId, String connectionId, String productCode);

    /**
     * List all currently active packages / add-ons / VAS for a connection.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @return list of active packages
     */
    List<ActivePackage> getActivePackages(String tenantId, String connectionId);

    /**
     * Query the status of a previously submitted activation request.
     *
     * @param tenantId the tenant identifier
     * @param activationId the activation request ID
     * @return current activation status
     */
    ActivationStatus getActivationStatus(String tenantId, String activationId);

    // ================================================================
    // Domain Objects
    // ================================================================

    /**
     * Result of an activate / deactivate operation.
     */
    record ActivationResult(
            boolean success,
            String activationId,
            String connectionId,
            String productCode,
            ActivationStatusCode status,
            Instant activatedAt,
            Instant expiresAt,
            String failureReason
    ) {}

    enum ActivationStatusCode {
        SUCCESS, PENDING, FAILED, TIMEOUT, ALREADY_ACTIVE, INELIGIBLE
    }

    /**
     * A currently active package on a connection.
     */
    record ActivePackage(
            String packageId,
            String connectionId,
            String productCode,
            String packageName,
            Instant activatedAt,
            Instant expiresAt,
            String status,            // ACTIVE, EXPIRED, PENDING_DEACTIVATION
            Boolean autoRenew
    ) {}

    /**
     * Status of an activation request.
     */
    record ActivationStatus(
            String activationId,
            String productCode,
            ActivationStatusCode status,
            Instant activatedAt,
            Instant expiresAt,
            String failureReason
    ) {}
}
