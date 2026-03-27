package com.squadron.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID id;
    private UUID tenantId;
    private String externalId;     // Was keycloakId - now generic for LDAP DN, OIDC sub, or Keycloak sub
    private String email;
    private String displayName;
    private String role;
    private String authProvider;   // "ldap", "oidc", "keycloak"
    private Set<String> roles;     // All assigned roles
    private Map<String, Object> settings;
    private Instant createdAt;
    private Instant updatedAt;
}
