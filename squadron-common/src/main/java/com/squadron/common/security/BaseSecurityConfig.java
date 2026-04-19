package com.squadron.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Base security configuration shared by all servlet-based Squadron microservices.
 * Subclasses only need to override {@link #additionalPermitAllPaths()} to expose
 * module-specific unauthenticated endpoints.
 */
public abstract class BaseSecurityConfig {

    private static final String[] DEFAULT_PERMIT_ALL = {
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info",
        "/v3/api-docs/**",
        "/swagger-ui/**"
    };

    @Value("${squadron.security.jwt.jwks-uri:http://localhost:8081/api/auth/jwks}")
    private String jwksUri;

    /**
     * Override to add module-specific paths that should be accessible without authentication.
     */
    protected String[] additionalPermitAllPaths() {
        return new String[0];
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String[] extra = additionalPermitAllPaths();
        String[] allPermitAll = new String[DEFAULT_PERMIT_ALL.length + extra.length];
        System.arraycopy(DEFAULT_PERMIT_ALL, 0, allPermitAll, 0, DEFAULT_PERMIT_ALL.length);
        System.arraycopy(extra, 0, allPermitAll, DEFAULT_PERMIT_ALL.length, extra.length);

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(allPermitAll).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(jwtDecoder())
                               .jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }
            // Also check Keycloak format
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof List<?> rl) {
                rl.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toString())));
            }
            return authorities;
        });
        return converter;
    }
}
