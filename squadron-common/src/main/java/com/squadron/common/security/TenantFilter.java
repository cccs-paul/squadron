package com.squadron.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servlet filter that extracts identity headers (set by the gateway) and populates
 * the thread-local TenantContext for downstream request processing.
 * <p>
 * Headers extracted:
 * - X-Tenant-Id: the tenant UUID
 * - X-User-Id: the user UUID
 * - X-User-Email: the user's email
 * - X-User-Roles: comma-separated list of roles
 * - X-Auth-Provider: the auth provider that authenticated the user (ldap, oidc, keycloak)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantIdHeader = request.getHeader(SecurityConstants.HEADER_TENANT_ID);
            String userIdHeader = request.getHeader(SecurityConstants.HEADER_USER_ID);
            String emailHeader = request.getHeader(SecurityConstants.HEADER_USER_EMAIL);
            String rolesHeader = request.getHeader(SecurityConstants.HEADER_USER_ROLES);
            String authProviderHeader = request.getHeader(SecurityConstants.HEADER_AUTH_PROVIDER);

            if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
                Set<String> roles = parseRoles(rolesHeader);

                TenantContext context = TenantContext.builder()
                        .tenantId(UUID.fromString(tenantIdHeader))
                        .userId(userIdHeader != null && !userIdHeader.isBlank()
                                ? UUID.fromString(userIdHeader) : null)
                        .email(emailHeader)
                        .roles(roles)
                        .authProvider(authProviderHeader)
                        .build();
                TenantContext.setContext(context);
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Set<String> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
