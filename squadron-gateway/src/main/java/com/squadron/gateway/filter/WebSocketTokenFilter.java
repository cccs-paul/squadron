package com.squadron.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Extracts JWT from the {@code access_token} query parameter on WebSocket upgrade
 * requests and copies it into the {@code Authorization} header so the downstream
 * Spring Security JWT decoder can authenticate the request.
 *
 * <p>Browsers cannot send custom headers on WebSocket HTTP upgrade requests,
 * so the frontend appends the JWT as a query parameter instead.  This filter
 * runs before the security filter chain (order&nbsp;-2) and transparently
 * bridges the two worlds.</p>
 */
@Component
public class WebSocketTokenFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Only intercept WebSocket upgrade requests that carry an access_token query param
        if (isWebSocketUpgrade(request)) {
            String token = request.getQueryParams().getFirst("access_token");
            if (token != null && !token.isBlank()) {
                // Inject the token as a Bearer Authorization header
                ServerHttpRequest mutated = request.mutate()
                        .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .build();
                return chain.filter(exchange.mutate().request(mutated).build());
            }
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Must run before the Spring Security WebFilter which runs at
        // SecurityWebFiltersOrder.HTTP_HEADERS_WRITER (-500) through
        // SecurityWebFiltersOrder.AUTHORIZATION (-600+).
        // Using -200 to guarantee we run before any security filter.
        return -200;
    }

    /**
     * Checks whether the request is a WebSocket upgrade by looking at the
     * {@code Upgrade} header and the request path.
     */
    private boolean isWebSocketUpgrade(ServerHttpRequest request) {
        String upgrade = request.getHeaders().getFirst(HttpHeaders.UPGRADE);
        if ("websocket".equalsIgnoreCase(upgrade)) {
            return true;
        }
        // Also match SockJS-style /websocket paths under /ws/**
        String path = request.getPath().value();
        return path.startsWith("/ws/") && path.endsWith("/websocket");
    }
}
