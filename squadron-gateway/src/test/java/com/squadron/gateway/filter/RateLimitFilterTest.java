package com.squadron.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimitFilter.
 *
 * Note: The RateLimitFilter uses .switchIfEmpty(chain.filter(exchange)) which eagerly
 * evaluates the Mono argument (constructing it even when the flatMap path is taken).
 * This means chain.filter() is invoked twice by Mockito recording: once from the flatMap
 * path and once from switchIfEmpty Mono construction. Tests account for this with atLeast().
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    @Mock
    private GatewayFilterChain chain;

    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter(redisTemplate, 100);
    }

    @Test
    void should_implementGlobalFilter() {
        assertThat(rateLimitFilter).isInstanceOf(org.springframework.cloud.gateway.filter.GlobalFilter.class);
    }

    @Test
    void should_implementOrdered() {
        assertThat(rateLimitFilter).isInstanceOf(org.springframework.core.Ordered.class);
    }

    @Test
    void should_returnOrderZero() {
        assertThat(rateLimitFilter.getOrder()).isEqualTo(0);
    }

    @Test
    void should_passThrough_when_noSecurityContext() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // No security context set -- the switchIfEmpty path should be taken
        StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, atLeastOnce()).filter(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void should_allowRequest_when_firstRequestInWindow() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Jwt jwt = createJwt("user-123");
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate_limit:user-123")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(eq("rate_limit:user-123"), any(Duration.class))).thenReturn(Mono.just(true));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = rateLimitFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        verify(redisTemplate).expire(eq("rate_limit:user-123"), eq(Duration.ofSeconds(1)));
        verify(chain, atLeastOnce()).filter(exchange);
    }

    @Test
    void should_allowRequest_when_underRateLimit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Jwt jwt = createJwt("user-456");
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate_limit:user-456")).thenReturn(Mono.just(50L));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = rateLimitFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        verify(chain, atLeastOnce()).filter(exchange);
    }

    @Test
    void should_rejectRequest_when_overRateLimit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Jwt jwt = createJwt("user-789");
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate_limit:user-789")).thenReturn(Mono.just(101L));
        // Must stub chain.filter because switchIfEmpty eagerly evaluates the argument
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = rateLimitFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void should_allowRequest_when_exactlyAtLimit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Jwt jwt = createJwt("user-limit");
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate_limit:user-limit")).thenReturn(Mono.just(100L));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = rateLimitFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        verify(chain, atLeastOnce()).filter(exchange);
    }

    @Test
    void should_useCorrectRedisKey_when_userAuthenticated() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Jwt jwt = createJwt("specific-user-id");
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate_limit:specific-user-id")).thenReturn(Mono.just(2L));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = rateLimitFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        verify(valueOps).increment("rate_limit:specific-user-id");
    }

    @Test
    void should_respectCustomRateLimit() {
        // Create filter with a low rate limit of 5
        RateLimitFilter lowLimitFilter = new RateLimitFilter(redisTemplate, 5);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Jwt jwt = createJwt("user-low");
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate_limit:user-low")).thenReturn(Mono.just(6L));
        // Must stub chain.filter because switchIfEmpty eagerly evaluates the argument
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = lowLimitFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void should_setExpiryOnFirstRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Jwt jwt = createJwt("user-expiry");
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate_limit:user-expiry")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = rateLimitFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        verify(redisTemplate).expire("rate_limit:user-expiry", Duration.ofSeconds(1));
    }

    @Test
    void should_notSetExpiry_when_notFirstRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        Jwt jwt = createJwt("user-noexpiry");
        SecurityContextImpl securityContext = new SecurityContextImpl(
                new TestingAuthenticationToken(jwt, null));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate_limit:user-noexpiry")).thenReturn(Mono.just(5L));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = rateLimitFilter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));

        StepVerifier.create(result)
                .verifyComplete();

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void should_beAnnotatedWithComponent() {
        var annotation = RateLimitFilter.class
                .getAnnotation(org.springframework.stereotype.Component.class);
        assertThat(annotation).isNotNull();
    }

    private Jwt createJwt(String subject) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", subject)
        );
    }
}
