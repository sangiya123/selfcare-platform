package com.selfcare.config.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Component catalog item — the per-tenant registry of renderable widgets.
 *
 * Authored in the admin portal (selfcare Studio component palette) and stored
 * in Mongo. Compiled into the Experience Manifest `components` section, so the
 * mobile app renders labels/icons/categories straight from operator config —
 * never hardcoded (v6 rule #1). Only PUBLISHED + enabled items reach a compiled
 * manifest.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "component_catalog")
@CompoundIndex(name = "tenant_env_component",
               def = "{'tenantId': 1, 'environment': 1, 'componentId': 1}",
               unique = true)
public class ComponentCatalogItem {

    @Id
    private String id;

    private String tenantId;
    private String environment;

    /** Renderer component ID — must exist in the known-components registry. */
    @Indexed
    private String componentId;

    /** Admin-facing label ("Bill Card"). */
    private String label;
    private String description;

    /** Palette icon token (emoji or icon reference resolved by the client). */
    private String icon;

    /** Schema categories: DISPLAY, INPUT, CONTAINER, NAVIGATION, FEEDBACK, ... */
    private String category;

    /** Privilege classification: display | read | write. */
    private String security = "display";

    private List<String> platforms;
    private String minAppVersion;

    private List<String> analyticsEvents;
    private List<String> variants;

    /**
     * Universal primitive the component renders (e.g. "UniversalBox").
     * New features compose existing primitives — no app code per feature
     * (ADR-009). Absent = availability-gate only, no client resolve.
     */
    private String primitive;

    /**
     * Immutable primitive config recipe authored in the admin portal (DB) and
     * compiled into the manifest. The mobile registry merges this with layout
     * props when resolving the component.
     */
    private Map<String, Object> config;

    /** When false the item is excluded from compiled manifests. */
    private boolean enabled = true;

    /** DRAFT | PUBLISHED | ARCHIVED. */
    @Indexed
    private String status = "DRAFT";

    private String owner;
    private String team;
    private List<String> tags;

    private String createdBy;
    private Instant createdAt;
    private String publishedBy;
    private Instant publishedAt;
    private Instant updatedAt;

    @Version
    private Long mongoVersion;
}