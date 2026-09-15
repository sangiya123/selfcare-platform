package com.selfcare.platform.common.web;

/**
 * Thrown when an authenticated user lacks permission for an action.
 * Maps to 403 Forbidden.
 *
 * Example: attempting to pay for a connection not in user's linked list.
 */
public class ForbiddenException extends RuntimeException {

    private final String action;
    private final String target;

    public ForbiddenException(String message) {
        super(message);
        this.action = null;
        this.target = null;
    }

    public ForbiddenException(String action, String target) {
        super(String.format("Forbidden: action='%s', target='%s'", action, target));
        this.action = action;
        this.target = target;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }
}