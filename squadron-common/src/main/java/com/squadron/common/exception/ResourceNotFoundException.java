package com.squadron.common.exception;

public class ResourceNotFoundException extends SquadronException {

    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super("Resource " + resourceType + " not found: " + resourceId, "NOT_FOUND");
    }

    public ResourceNotFoundException(String message) {
        super(message, "NOT_FOUND");
    }
}
