package com.squadron.common.security;

/**
 * Access levels that can be granted to users or security groups on resources.
 */
public enum AccessLevel {
    NONE,
    READ,       // Read-only access
    WRITE,      // Read-write access
    ADMIN       // Full admin access (includes delete, manage permissions)
}
