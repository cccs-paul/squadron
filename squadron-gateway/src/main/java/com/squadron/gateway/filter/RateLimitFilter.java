package com.squadron.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final long maxRequestsPerSecond;

    public RateLimitFilter(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${squadron.gateway.rate-limit:100}") long maxRequestsPerSecond) {
        this.redisTemplate = redisTemplate;
        this.maxRequestsPerSecond = maxRequestsPerSecond;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.getPrincipal() instanceof Jwt)
                .flatMap(auth -> {
                    Jwt jwt = (Jwt) auth.getPrincipal();
                    String userId = jwt.getSubject();
                    String key = "rate_limit:" + userId;

                    return redisTemplate.opsForValue().increment(key)
                            .flatMap(count -> {
                                if (count == 1) {
                                    return redisTemplate.expire(key, Duration.ofSeconds(1))
                                            .then(chain.filter(exchange));
                                }
                                if (count > maxRequestsPerSecond) {
                                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                                    return exchange.getResponse().setComplete();
                                }
                                return chain.filter(exchange);
                            });
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
