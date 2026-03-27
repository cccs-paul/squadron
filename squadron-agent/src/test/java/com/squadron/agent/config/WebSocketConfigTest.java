package com.squadron.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

    @Test
    void should_configureMessageBroker() {
        WebSocketConfig config = new WebSocketConfig();
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

        when(registry.enableSimpleBroker(anyString())).thenReturn(null);
        when(registry.setApplicationDestinationPrefixes(anyString())).thenReturn(registry);

        config.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic");
        verify(registry).setApplicationDestinationPrefixes("/app");
    }

    @Test
    void should_registerStompEndpoints() {
        WebSocketConfig config = new WebSocketConfig();
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);

        when(registry.addEndpoint("/ws/agent")).thenReturn(registration);
        when(registration.setAllowedOrigins("*")).thenReturn(registration);
        when(registration.withSockJS()).thenReturn(null);

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws/agent");
        verify(registration).setAllowedOrigins("*");
        verify(registration).withSockJS();
    }
}
