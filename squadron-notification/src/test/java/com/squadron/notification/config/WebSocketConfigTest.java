package com.squadron.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

    @Test
    void should_configureMessageBroker_when_called() {
        WebSocketConfig config = new WebSocketConfig();
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

        when(registry.enableSimpleBroker(any(String[].class))).thenReturn(null);
        when(registry.setApplicationDestinationPrefixes(any(String[].class))).thenReturn(registry);

        config.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic");
        verify(registry).setApplicationDestinationPrefixes("/app");
    }

    @Test
    void should_registerStompEndpoints_when_called() {
        WebSocketConfig config = new WebSocketConfig();
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);

        when(registry.addEndpoint("/ws/notifications")).thenReturn(registration);
        when(registration.setAllowedOrigins("*")).thenReturn(registration);

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws/notifications");
        verify(registration).setAllowedOrigins("*");
        verify(registration).withSockJS();
    }
}
