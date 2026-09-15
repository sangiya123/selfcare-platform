package com.selfcare.platform.common.adapter;

import java.time.Instant;
import java.util.List;

/**
 * Provider interface for retrieving location-based information from operator
 * systems. Cross-industry — used for tower lookup (telco), branch locator
 * (banking), provider network (insurance), etc.
 *
 * Implementations live in industry packs: telco/dialog, insurance/aia, ...
 *
 * @see ADR-019: Provider Orchestration
 */
public interface LocationProvider extends ApiAdapter {

    /**
     * Get the current cell tower for a given MSISDN (telco only).
     * Returns null if not telco.
     */
    CellTower getCurrentCellTower(String tenantId, String connectionId);

    /**
     * Get the cell tower history for a connection (last N hours).
     */
    List<CellTower> getCellTowerHistory(String tenantId, String connectionId, Instant from, Instant to);

    /**
     * Get the roaming zone for a connection at the given time.
     */
    RoamingZone getRoamingZone(String tenantId, String connectionId, Instant at);

    /**
     * Geocode an address to coordinates.
     */
    GeoCoordinates geocode(String tenantId, String address);

    /**
     * Reverse-geocode coordinates to an address.
     */
    String reverseGeocode(String tenantId, double latitude, double longitude);

    /**
     * Get nearby branches / stores / agents (banking, insurance).
     */
    List<Branch> getNearbyBranches(String tenantId, double latitude, double longitude, double radiusKm, int limit);

    /**
     * Get nearby service points (e.g. network coverage towers, WiFi hotspots).
     */
    List<ServicePoint> getNearbyServicePoints(String tenantId, double latitude, double longitude, double radiusKm, ServicePointType type);

    // --- Result types ---

    record CellTower(
        String towerId,
        String mcc,
        String mnc,
        String lac,
        String cellId,
        double latitude,
        double longitude,
        String technology,   // 2G/3G/4G/5G
        Instant observedAt
    ) {}

    record RoamingZone(
        String countryCode,
        String countryName,
        String networkName,
        String zoneId,         // e.g. "zone-1", "as-pacific"
        String zoneName,
        boolean isHomeCountry
    ) {}

    record GeoCoordinates(
        double latitude,
        double longitude,
        double accuracyMeters,
        String formattedAddress
    ) {}

    record Branch(
        String branchId,
        String name,
        String type,            // BRANCH, ATM, AGENT
        String address,
        double latitude,
        double longitude,
        String phone,
        String hours,
        List<String> services
    ) {}

    record ServicePoint(
        String id,
        ServicePointType type,
        String name,
        double latitude,
        double longitude,
        String address
    ) {}

    enum ServicePointType {
        TOWER,           // Network tower
        WIFI_HOTSPOT,    // Public WiFi
        ROAMING_PARTNER, // Roaming partner location
        CHARGING_STATION // EV charging / device charging
    }
}
