package com.selfcare.platform.common.adapter;

import java.util.List;

/**
 * Connection / Entitlement Provider contract for telco industry packs.
 *
 * Each operator (Dialog, Hutch, Airtel, ...) implements this interface to
 * expose the canonical mobile connection and entitlement capabilities
 * required by the connection-management service and BFFs:
 *
 * <ul>
 *   <li>List all connections (primary + linked) for a subscriber</li>
 *   <li>Get a specific connection by its connection ID (MSISDN)</li>
 *   <li>Fetch the current service status of a connection (active, suspended, etc.)</li>
 *   <li>Fetch SIM details (SIM number, ICCID, device info)</li>
 * </ul>
 *
 * All methods take {@code tenantId} as the first argument so a single
 * implementation can serve many tenants. Upstream config (URL, credentials)
 * is loaded at runtime from {@link com.selfcare.platform.common.tenant.TenantConfigurationService}.
 */
public interface ConnectionProvider extends ApiAdapter {

    /**
     * Get all connections (primary and linked) for a given primary MSISDN.
     *
     * @param tenantId the tenant identifier (e.g. {@code "dialog-lk"})
     * @param primaryMsisdn the primary MSISDN of the subscriber
     * @return list of connections; never null
     */
    List<Connection> getConnections(String tenantId, String primaryMsisdn);

    /**
     * Get a specific connection by its ID.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID (MSISDN)
     * @return the connection, or null if not found
     */
    Connection getConnection(String tenantId, String connectionId);

    /**
     * Get the current service status of a connection.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @return service status
     */
    ServiceStatus getServiceStatus(String tenantId, String connectionId);

    /**
     * Get SIM card details for a connection.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @return SIM details, or null if not available
     */
    SimDetails getSimDetails(String tenantId, String connectionId);

    // ================================================================
    // Domain Objects
    // ================================================================

    /**
     * Represents a subscriber connection (primary SIM or linked eSIM/data-SIM).
     */
    record Connection(
            String connectionId,
            String msisdn,
            String connectionType,   // PRIMARY, ESIM, DATA_SIM, IOT
            String status,           // ACTIVE, SUSPENDED, TERMINATED, PENDING
            String tariffPlan,
            String activationDate,
            String networkType,      // 5G, 4G, 3G, 2G
            String imsi,
            String iccid,
            String primaryConnectionId  // null for the primary connection itself
    ) {}

    /**
     * Service status of a connection.
     */
    record ServiceStatus(
            String connectionId,
            String status,           // ACTIVE, SUSPENDED, BARRED, PENDING_ACTIVATION
            String suspensionReason,
            Boolean isRoaming,
            Boolean isDataEnabled,
            Boolean isVoiceEnabled,
            Boolean isSmsEnabled,
            Integer dataAllowanceMb,
            Integer dataUsedMb,
            String networkSlice
    ) {}

    /**
     * SIM card details.
     */
    record SimDetails(
            String connectionId,
            String iccid,
            String imsi,
            String simType,          // MICRO, NANO, ESIM, EMBEDDED
            String simStatus,        // ACTIVE, INACTIVE, LOST, BLOCKED
            String pukCode           // masked, e.g. "****1234"
    ) {}
}
