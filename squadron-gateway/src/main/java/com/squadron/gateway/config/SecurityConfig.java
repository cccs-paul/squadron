package com.squadron.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${squadron.security.auth-mode:squadron}")
    private String authMode; // "squadron", "keycloak", "hybrid"

    @Value("${squadron.security.jwt.jwks-uri:#{null}}")
    private String squadronJwksUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:#{null}}")
    private String keycloakJwksUri;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> {})
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                            "/actuator/health", "/actuator/info",
                            "/api/auth/**", "/api/identity/auth/**",
                            "/.well-known/**"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                )
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        // For squadron mode, use the squadron-identity JWKS endpoint
        // For keycloak mode, use the Keycloak JWKS endpoint
        // For hybrid mode, try squadron first, then keycloak
        String jwksUri = switch (authMode) {
            case "keycloak" -> keycloakJwksUri;
            default -> squadronJwksUri != null ? squadronJwksUri
                    : (keycloakJwksUri != null ? keycloakJwksUri : "http://localhost:8081/api/auth/jwks");
        };

        if ("hybrid".equals(authMode) && keycloakJwksUri != null && squadronJwksUri != null) {
            // Create a composite decoder that tries both
            NimbusReactiveJwtDecoder squadronDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(squadronJwksUri).build();
            NimbusReactiveJwtDecoder keycloakDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(keycloakJwksUri).build();
            return token -> squadronDecoder.decode(token)
                    .onErrorResume(e -> keycloakDecoder.decode(token));
        }

        return NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();

            // Extract roles from Squadron JWT format
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }

            // Extract roles from Keycloak JWT format (realm_access.roles)
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                Object realmRoles = realmAccess.get("roles");
                if (realmRoles instanceof List<?> rl) {
                    rl.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
                }
            }

            return authorities;
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
