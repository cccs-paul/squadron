package com.squadron.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RequestLoggingFilter.
 */
@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private RequestLoggingFilter requestLoggingFilter;

    @BeforeEach
    void setUp() {
        requestLoggingFilter = new RequestLoggingFilter();
    }

    @Test
    void should_implementGlobalFilter() {
        assertThat(requestLoggingFilter).isInstanceOf(org.springframework.cloud.gateway.filter.GlobalFilter.class);
    }

    @Test
    void should_implementOrdered() {
        assertThat(requestLoggingFilter).isInstanceOf(org.springframework.core.Ordered.class);
    }

    @Test
    void should_returnOrderMinusOne() {
        assertThat(requestLoggingFilter.getOrder()).isEqualTo(-1);
    }

    @Test
    void should_invokeChainFilter_when_getRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(requestLoggingFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void should_invokeChainFilter_when_postRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/tasks").build());

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(requestLoggingFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void should_invokeChainFilter_when_putRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/api/tasks/123").build());

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(requestLoggingFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void should_invokeChainFilter_when_deleteRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.delete("/api/tasks/123").build());

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(requestLoggingFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void should_invokeChainFilter_when_patchRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.patch("/api/tasks/123").build());

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(requestLoggingFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void should_propagateChainError_when_downstreamFails() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/error").build());

        RuntimeException expectedError = new RuntimeException("Downstream error");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.error(expectedError));

        StepVerifier.create(requestLoggingFilter.filter(exchange, chain))
                .expectError(RuntimeException.class)
                .verify();

        verify(chain).filter(exchange);
    }

    @Test
    void should_completeSuccessfully_when_chainCompletes() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/health").build());

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(requestLoggingFilter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void should_callChainExactlyOnce_when_filterInvoked() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        requestLoggingFilter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(exchange);
    }

    @Test
    void should_haveHigherPriority_than_rateLimitFilter() {
        RateLimitFilter rateLimitFilter = new RateLimitFilter(null, 100);
        // Lower order = higher priority
        assertThat(requestLoggingFilter.getOrder()).isLessThan(rateLimitFilter.getOrder());
    }

    @Test
    void should_haveHigherPriority_than_tenantHeaderFilter() {
        TenantHeaderFilter tenantHeaderFilter = new TenantHeaderFilter();
        // Lower order = higher priority
        assertThat(requestLoggingFilter.getOrder()).isLessThan(tenantHeaderFilter.getOrder());
    }

    @Test
    void should_beAnnotatedWithComponent() {
        var annotation = RequestLoggingFilter.class
                .getAnnotation(org.springframework.stereotype.Component.class);
        assertThat(annotation).isNotNull();
    }
}
