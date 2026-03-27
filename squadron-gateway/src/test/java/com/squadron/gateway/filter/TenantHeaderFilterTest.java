package com.squadron.gateway.filter;

import com.squadron.common.security.SecurityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URL;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantHeaderFilter.
 */
@ExtendWith(MockitoExtension.class)
class TenantHeaderFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private TenantHeaderFilter tenantHeaderFilter;

    @BeforeEach
    void setUp() {
        tenantHeaderFilter = new TenantHeaderFilter();
    }

    @Test
    void should_implementGlobalFilter() {
        assertThat(tenantHeaderFilter).isInstanceOf(org.springframework.cloud.gateway.filter.GlobalFilter.class);
    }

    @Test
    void should_implementOrdered() {
        assertThat(tenantHeaderFilter).isInstanceOf(org.springframework.core.Ordered.class);
    }

    @Test
    void should_returnOrderOne() {
        assertThat(tenantHeaderFilter.getOrder()).isEqualTo(1);
    }

    @Test
    void should_passThrough_when_noSecurityContext() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(tenantHeaderFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void should_setTenantIdHeader_when_squadronJwt() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-123");
        claims.put(SecurityConstants.CLAIM_TENANT_ID, "tenant-abc");
        claims.put(SecurityConstants.CLAIM_USER_ID, "user-123");
        claims.put(SecurityConstants.CLAIM_EMAIL, "user@example.com");
        claims.put(SecurityConstants.CLAIM_ROLES, List.of("developer", "qa"));
        claims.put(SecurityConstants.CLAIM_AUTH_PROVIDER, "oidc");

        Jwt jwt = createJwt("user-123", claims, null);
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        Mono<Void> result = tenantHeaderFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        HttpHeaders headers = capturedExchange.getRequest().getHeaders();

        assertThat(headers.getFirst(SecurityConstants.HEADER_TENANT_ID)).isEqualTo("tenant-abc");
        assertThat(headers.getFirst(SecurityConstants.HEADER_USER_ID)).isEqualTo("user-123");
        assertThat(headers.getFirst(SecurityConstants.HEADER_USER_EMAIL)).isEqualTo("user@example.com");
        assertThat(headers.getFirst(SecurityConstants.HEADER_USER_ROLES)).isEqualTo("developer,qa");
        assertThat(headers.getFirst(SecurityConstants.HEADER_AUTH_PROVIDER)).isEqualTo("oidc");
    }

    @Test
    void should_setUserIdFromSubject_when_userIdClaimMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "subject-user-id");
        // No user_id claim

        Jwt jwt = createJwt("subject-user-id", claims, null);
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        Mono<Void> result = tenantHeaderFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        HttpHeaders headers = capturedExchange.getRequest().getHeaders();

        assertThat(headers.getFirst(SecurityConstants.HEADER_USER_ID)).isEqualTo("subject-user-id");
    }

    @Test
    void should_usePreferredUsername_when_emailClaimMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-123");
        claims.put("preferred_username", "jdoe@example.com");
        // No email claim

        Jwt jwt = createJwt("user-123", claims, null);
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        Mono<Void> result = tenantHeaderFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        HttpHeaders headers = capturedExchange.getRequest().getHeaders();

        assertThat(headers.getFirst(SecurityConstants.HEADER_USER_EMAIL)).isEqualTo("jdoe@example.com");
    }

    @Test
    void should_extractKeycloakRoles_when_squadronRolesMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Map<String, Object> realmAccess = Map.of("roles", List.of("admin", "user"));
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-123");
        claims.put("realm_access", realmAccess);
        // No squadron-format roles

        Jwt jwt = createJwt("user-123", claims, null);
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        Mono<Void> result = tenantHeaderFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        HttpHeaders headers = capturedExchange.getRequest().getHeaders();

        assertThat(headers.getFirst(SecurityConstants.HEADER_USER_ROLES)).isEqualTo("admin,user");
    }

    @Test
    void should_detectKeycloakProvider_when_issuerContainsRealms() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-123");
        claims.put("iss", "http://keycloak:8080/realms/squadron");
        // No auth_provider claim

        Jwt jwt = createJwt("user-123", claims, "http://keycloak:8080/realms/squadron");
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        Mono<Void> result = tenantHeaderFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        HttpHeaders headers = capturedExchange.getRequest().getHeaders();

        assertThat(headers.getFirst(SecurityConstants.HEADER_AUTH_PROVIDER))
                .isEqualTo(SecurityConstants.AUTH_PROVIDER_KEYCLOAK);
    }

    @Test
    void should_detectOidcProvider_when_issuerDoesNotContainKeycloak() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-123");
        // No auth_provider claim, issuer is not keycloak

        Jwt jwt = createJwt("user-123", claims, "http://squadron-identity:8081");
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        Mono<Void> result = tenantHeaderFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        HttpHeaders headers = capturedExchange.getRequest().getHeaders();

        assertThat(headers.getFirst(SecurityConstants.HEADER_AUTH_PROVIDER))
                .isEqualTo(SecurityConstants.AUTH_PROVIDER_OIDC);
    }

    @Test
    void should_stripExistingIdentityHeaders_when_presentInRequest() {
        // Pre-existing headers that should be stripped to prevent injection
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header(SecurityConstants.HEADER_TENANT_ID, "injected-tenant")
                        .header(SecurityConstants.HEADER_USER_ID, "injected-user")
                        .header(SecurityConstants.HEADER_USER_EMAIL, "injected@evil.com")
                        .header(SecurityConstants.HEADER_USER_ROLES, "admin")
                        .header(SecurityConstants.HEADER_AUTH_PROVIDER, "injected")
                        .build());

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "real-user");
        claims.put(SecurityConstants.CLAIM_TENANT_ID, "real-tenant");
        claims.put(SecurityConstants.CLAIM_USER_ID, "real-user");
        claims.put(SecurityConstants.CLAIM_EMAIL, "real@example.com");
        claims.put(SecurityConstants.CLAIM_ROLES, List.of("developer"));
        claims.put(SecurityConstants.CLAIM_AUTH_PROVIDER, "oidc");

        Jwt jwt = createJwt("real-user", claims, null);
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        Mono<Void> result = tenantHeaderFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        HttpHeaders headers = capturedExchange.getRequest().getHeaders();

        // Should use JWT values, not the injected ones
        assertThat(headers.getFirst(SecurityConstants.HEADER_TENANT_ID)).isEqualTo("real-tenant");
        assertThat(headers.getFirst(SecurityConstants.HEADER_USER_ID)).isEqualTo("real-user");
        assertThat(headers.getFirst(SecurityConstants.HEADER_USER_EMAIL)).isEqualTo("real@example.com");
        assertThat(headers.getFirst(SecurityConstants.HEADER_USER_ROLES)).isEqualTo("developer");
        assertThat(headers.getFirst(SecurityConstants.HEADER_AUTH_PROVIDER)).isEqualTo("oidc");
    }

    @Test
    void should_notSetTenantIdHeader_when_tenantIdClaimMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-123");
        // No tenant_id claim

        Jwt jwt = createJwt("user-123", claims, null);
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        Mono<Void> result = tenantHeaderFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        HttpHeaders headers = capturedExchange.getRequest().getHeaders();

        assertThat(headers.getFirst(SecurityConstants.HEADER_TENANT_ID)).isNull();
    }

    @Test
    void should_notSetRolesHeader_when_noRolesPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-123");
        // No roles claim and no realm_access

        Jwt jwt = createJwt("user-123", claims, null);
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        Mono<Void> result = tenantHeaderFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        HttpHeaders headers = capturedExchange.getRequest().getHeaders();

        assertThat(headers.getFirst(SecurityConstants.HEADER_USER_ROLES)).isNull();
    }

    @Test
    void should_preferSquadronRoles_when_bothFormatsPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Map<String, Object> realmAccess = Map.of("roles", List.of("keycloak-admin"));
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-123");
        claims.put(SecurityConstants.CLAIM_ROLES, List.of("squadron-developer"));
        claims.put("realm_access", realmAccess);

        Jwt jwt = createJwt("user-123", claims, null);
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        Mono<Void> result = tenantHeaderFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        HttpHeaders headers = capturedExchange.getRequest().getHeaders();

        // Should use squadron format roles, not Keycloak
        assertThat(headers.getFirst(SecurityConstants.HEADER_USER_ROLES)).isEqualTo("squadron-developer");
    }

    @Test
    void should_beAnnotatedWithComponent() {
        var annotation = TenantHeaderFilter.class
                .getAnnotation(org.springframework.stereotype.Component.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void should_handleNullIssuer_when_detectingAuthProvider() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-123");
        // No auth_provider claim, and null issuer

        Jwt jwt = createJwt("user-123", claims, null);
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        Mono<Void> result = tenantHeaderFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        HttpHeaders headers = capturedExchange.getRequest().getHeaders();

        // With null issuer, should default to OIDC
        assertThat(headers.getFirst(SecurityConstants.HEADER_AUTH_PROVIDER))
                .isEqualTo(SecurityConstants.AUTH_PROVIDER_OIDC);
    }

    @Test
    void should_handleEmptyRolesList_when_squadronRolesEmpty() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Map<String, Object> realmAccess = Map.of("roles", List.of("fallback-role"));
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-123");
        claims.put(SecurityConstants.CLAIM_ROLES, List.of()); // empty list
        claims.put("realm_access", realmAccess);

        Jwt jwt = createJwt("user-123", claims, null);
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        Mono<Void> result = tenantHeaderFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        HttpHeaders headers = capturedExchange.getRequest().getHeaders();

        // When squadron roles list is empty, should fall through to Keycloak format
        assertThat(headers.getFirst(SecurityConstants.HEADER_USER_ROLES)).isEqualTo("fallback-role");
    }

    private Jwt createJwt(String subject, Map<String, Object> claims, String issuer) {
        Map<String, Object> allClaims = new HashMap<>(claims);
        allClaims.put("sub", subject);
        if (issuer != null) {
            try {
                allClaims.put("iss", new URL(issuer));
            } catch (Exception e) {
                allClaims.put("iss", issuer);
            }
        }

        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                allClaims
        );
    }
}
