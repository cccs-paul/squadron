package com.squadron.gateway.filter;

import com.squadron.common.security.SecurityConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TenantHeaderFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.getPrincipal() instanceof Jwt)
                .flatMap(auth -> {
                    Jwt jwt = (Jwt) auth.getPrincipal();
                    ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

                    // Strip existing identity headers to prevent injection
                    requestBuilder.headers(headers -> {
                        headers.remove(SecurityConstants.HEADER_TENANT_ID);
                        headers.remove(SecurityConstants.HEADER_USER_ID);
                        headers.remove(SecurityConstants.HEADER_USER_EMAIL);
                        headers.remove(SecurityConstants.HEADER_USER_ROLES);
                        headers.remove(SecurityConstants.HEADER_AUTH_PROVIDER);
                    });

                    // Extract from Squadron JWT format
                    String tenantId = jwt.getClaimAsString(SecurityConstants.CLAIM_TENANT_ID);
                    String userId = jwt.getClaimAsString(SecurityConstants.CLAIM_USER_ID);
                    String email = jwt.getClaimAsString(SecurityConstants.CLAIM_EMAIL);
                    String authProvider = jwt.getClaimAsString(SecurityConstants.CLAIM_AUTH_PROVIDER);

                    // Extract roles - try Squadron format first, then Keycloak format
                    List<String> roles = jwt.getClaimAsStringList(SecurityConstants.CLAIM_ROLES);
                    if (roles == null || roles.isEmpty()) {
                        // Try Keycloak format
                        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
                        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> rl) {
                            roles = rl.stream()
                                    .filter(String.class::isInstance)
                                    .map(String.class::cast)
                                    .collect(Collectors.toList());
                        }
                    }

                    // For Keycloak, userId falls back to JWT subject
                    if (userId == null) {
                        userId = jwt.getSubject();
                    }
                    if (email == null) {
                        email = jwt.getClaimAsString("preferred_username");
                    }
                    if (authProvider == null) {
                        // Detect from issuer
                        String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : "";
                        authProvider = issuer.contains("keycloak") || issuer.contains("/realms/")
                                ? SecurityConstants.AUTH_PROVIDER_KEYCLOAK
                                : SecurityConstants.AUTH_PROVIDER_OIDC;
                    }

                    // Set headers
                    if (tenantId != null) {
                        requestBuilder.header(SecurityConstants.HEADER_TENANT_ID, tenantId);
                    }
                    if (userId != null) {
                        requestBuilder.header(SecurityConstants.HEADER_USER_ID, userId);
                    }
                    if (email != null) {
                        requestBuilder.header(SecurityConstants.HEADER_USER_EMAIL, email);
                    }
                    if (roles != null && !roles.isEmpty()) {
                        requestBuilder.header(SecurityConstants.HEADER_USER_ROLES, String.join(",", roles));
                    }
                    if (authProvider != null) {
                        requestBuilder.header(SecurityConstants.HEADER_AUTH_PROVIDER, authProvider);
                    }

                    return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
