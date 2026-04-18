package com.squadron.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servlet filter that extracts identity headers (set by the gateway) and populates
 * the thread-local TenantContext for downstream request processing.
 * <p>
 * For unauthenticated requests (no tenant header), identity headers are stripped
 * to prevent header spoofing on permitAll paths.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter extends OncePerRequestFilter {

    private static final Set<String> IDENTITY_HEADERS = Set.of(
            SecurityConstants.HEADER_TENANT_ID.toLowerCase(),
            SecurityConstants.HEADER_USER_ID.toLowerCase(),
            SecurityConstants.HEADER_USER_ROLES.toLowerCase()
    );

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
                UUID tenantId;
                try {
                    tenantId = UUID.fromString(tenantIdHeader.trim());
                } catch (IllegalArgumentException e) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid X-Tenant-Id header");
                    return;
                }

                UUID userId = null;
                if (userIdHeader != null && !userIdHeader.isBlank()) {
                    try {
                        userId = UUID.fromString(userIdHeader.trim());
                    } catch (IllegalArgumentException e) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid X-User-Id header");
                        return;
                    }
                }

                Set<String> roles = parseRoles(rolesHeader);

                TenantContext context = TenantContext.builder()
                        .tenantId(tenantId)
                        .userId(userId)
                        .email(emailHeader)
                        .roles(roles)
                        .authProvider(authProviderHeader)
                        .build();
                TenantContext.setContext(context);

                filterChain.doFilter(request, response);
            } else {
                // Unauthenticated: strip identity headers to prevent spoofing
                filterChain.doFilter(new HeaderStrippingRequestWrapper(request), response);
            }
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

    /**
     * Request wrapper that strips identity headers to prevent spoofing on
     * unauthenticated (permitAll) paths.
     */
    private static class HeaderStrippingRequestWrapper extends HttpServletRequestWrapper {

        HeaderStrippingRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            if (IDENTITY_HEADERS.contains(name.toLowerCase())) {
                return null;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (IDENTITY_HEADERS.contains(name.toLowerCase())) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                String name = original.nextElement();
                if (!IDENTITY_HEADERS.contains(name.toLowerCase())) {
                    names.add(name);
                }
            }
            return Collections.enumeration(names);
        }
    }
}
