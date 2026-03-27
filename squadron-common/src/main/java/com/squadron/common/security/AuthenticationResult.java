package com.squadron.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Result of a successful authentication from any provider (LDAP, OIDC, Keycloak).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationResult {
    private String externalId;      // LDAP DN, OIDC sub, or Keycloak sub
    private String email;
    private String displayName;
    private Set<String> roles;      // Mapped from LDAP groups / OIDC claims / Keycloak roles
    private String authProvider;    // "ldap", "oidc", "keycloak"
    private Map<String, Object> attributes;  // Additional attributes from the provider

    // After user provisioning, these get set
    private UUID userId;
    private UUID tenantId;
}
