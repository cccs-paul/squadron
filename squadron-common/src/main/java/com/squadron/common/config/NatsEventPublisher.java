package com.squadron.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.event.SquadronEvent;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class NatsEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NatsEventPublisher.class);

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;

    public NatsEventPublisher(Connection natsConnection, ObjectMapper objectMapper) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
    }

    public void publish(String subject, SquadronEvent event) {
        try {
            byte[] data = objectMapper.writeValueAsBytes(event);
            natsConnection.publish(subject, data);
            log.debug("Published event {} to subject {}", event.getEventType(), subject);
        } catch (Exception e) {
            log.error("Failed to publish event {} to subject {}", event.getEventType(), subject, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    public CompletableFuture<Void> publishAsync(String subject, SquadronEvent event) {
        return CompletableFuture.runAsync(() -> publish(subject, event));
    }
}
