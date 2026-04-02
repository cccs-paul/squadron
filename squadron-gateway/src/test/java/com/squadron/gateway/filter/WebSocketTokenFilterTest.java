package com.squadron.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebSocketTokenFilter.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketTokenFilterTest {

    @Mock
    private WebFilterChain chain;

    private WebSocketTokenFilter filter;

    @BeforeEach
    void setUp() {
        filter = new WebSocketTokenFilter();
    }

    @Test
    void should_implementWebFilter() {
        assertThat(filter).isInstanceOf(org.springframework.web.server.WebFilter.class);
    }

    @Test
    void should_implementOrdered() {
        assertThat(filter).isInstanceOf(org.springframework.core.Ordered.class);
    }

    @Test
    void should_returnOrderMinus200() {
        assertThat(filter.getOrder()).isEqualTo(-200);
    }

    @Test
    void should_beAnnotatedWithComponent() {
        var annotation = WebSocketTokenFilter.class
                .getAnnotation(org.springframework.stereotype.Component.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void should_injectAuthorizationHeader_when_webSocketUpgradeWithToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/notifications/websocket?access_token=my-jwt-token-123")
                        .header(HttpHeaders.UPGRADE, "websocket")
                        .build());

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ServerWebExchange captured = exchangeCaptor.getValue();
        String authHeader = captured.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        assertThat(authHeader).isEqualTo("Bearer my-jwt-token-123");
    }

    @Test
    void should_injectAuthorizationHeader_when_sockJsWebSocketPathWithToken() {
        // SockJS-style path ending in /websocket under /ws/ — no Upgrade header
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/agent/websocket?access_token=agent-token-456")
                        .build());

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ServerWebExchange captured = exchangeCaptor.getValue();
        String authHeader = captured.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        assertThat(authHeader).isEqualTo("Bearer agent-token-456");
    }

    @Test
    void should_passThrough_when_webSocketUpgradeWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/notifications/websocket")
                        .header(HttpHeaders.UPGRADE, "websocket")
                        .build());

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Should pass through the original exchange unchanged
        verify(chain).filter(exchange);
    }

    @Test
    void should_passThrough_when_regularApiRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects")
                        .build());

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void should_passThrough_when_regularApiRequestWithToken() {
        // Token in query param on a non-WS request should be ignored
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects?access_token=should-be-ignored")
                        .build());

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void should_passThrough_when_blankToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/notifications/websocket?access_token=   ")
                        .header(HttpHeaders.UPGRADE, "websocket")
                        .build());

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void should_passThrough_when_emptyToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/notifications/websocket?access_token=")
                        .header(HttpHeaders.UPGRADE, "websocket")
                        .build());

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void should_handleUpgradeHeaderCaseInsensitive() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/notifications/websocket?access_token=case-token")
                        .header(HttpHeaders.UPGRADE, "WebSocket")
                        .build());

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ServerWebExchange captured = exchangeCaptor.getValue();
        String authHeader = captured.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        assertThat(authHeader).isEqualTo("Bearer case-token");
    }

    @Test
    void should_passThrough_when_nonWsPathWithUpgradeHeader() {
        // Upgrade header on a non-/ws/ path — should still inject since it IS a WS upgrade
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/some-endpoint?access_token=some-token")
                        .header(HttpHeaders.UPGRADE, "websocket")
                        .build());

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // The Upgrade header is present so it IS a websocket upgrade — token should be injected
        ServerWebExchange captured = exchangeCaptor.getValue();
        String authHeader = captured.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        assertThat(authHeader).isEqualTo("Bearer some-token");
    }

    @Test
    void should_runBeforeSpringSecurityAndOtherGatewayFilters() {
        // WebSocketTokenFilter must be -200, before Spring Security (-100),
        // RequestLoggingFilter (-1), RateLimit (0), TenantHeader (1)
        assertThat(filter.getOrder()).isEqualTo(-200);
        assertThat(filter.getOrder()).isLessThan(-100); // Spring Security WebFilterChainProxy order
        assertThat(filter.getOrder()).isLessThan(new RequestLoggingFilter().getOrder());
        assertThat(filter.getOrder()).isLessThan(new TenantHeaderFilter().getOrder());
    }
}
