package com.omobio.platform.common.web;

/**
 * Thrown when a requested resource is not found.
 * Maps to 404 Not Found.
 */
public class NotFoundException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    public NotFoundException(String message) {
        super(message);
        this.resourceType = null;
        this.resourceId = null;
    }

    public NotFoundException(String resourceType, String resourceId) {
        super(String.format("%s not found: %s", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}