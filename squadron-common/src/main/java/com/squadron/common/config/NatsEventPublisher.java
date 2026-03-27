package com.squadron.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.event.SquadronEvent;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.api.PublishAck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class NatsEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NatsEventPublisher.class);

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;
    private JetStream jetStream;

    public NatsEventPublisher(Connection natsConnection, ObjectMapper objectMapper) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    public void setJetStream(JetStream jetStream) {
        this.jetStream = jetStream;
    }

    public void publish(String subject, SquadronEvent event) {
        try {
            byte[] data = objectMapper.writeValueAsBytes(event);

            if (jetStream != null) {
                try {
                    PublishAck ack = jetStream.publish(subject, data);
                    log.debug("Published event {} to JetStream subject {} (stream: {}, seq: {})",
                            event.getEventType(), subject, ack.getStream(), ack.getSeqno());
                    return;
                } catch (Exception e) {
                    log.debug("JetStream publish failed for subject {}, falling back to core NATS: {}",
                            subject, e.getMessage());
                }
            }

            natsConnection.publish(subject, data);
            log.debug("Published event {} to core NATS subject {}", event.getEventType(), subject);
        } catch (Exception e) {
            log.error("Failed to publish event {} to subject {}", event.getEventType(), subject, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    public CompletableFuture<Void> publishAsync(String subject, SquadronEvent event) {
        return CompletableFuture.runAsync(() -> publish(subject, event));
    }

    /**
     * Publishes raw bytes to the given subject, using JetStream if available
     * with fallback to core NATS. This is useful for publishing non-SquadronEvent
     * payloads (e.g., audit events) while still benefiting from JetStream
     * durable delivery.
     *
     * @param subject the NATS subject to publish to
     * @param data    the raw byte payload
     */
    public void publishRaw(String subject, byte[] data) {
        if (jetStream != null) {
            try {
                PublishAck ack = jetStream.publish(subject, data);
                log.debug("Published raw data to JetStream subject {} (stream: {}, seq: {})",
                        subject, ack.getStream(), ack.getSeqno());
                return;
            } catch (Exception e) {
                log.debug("JetStream publish failed for subject {}, falling back to core NATS: {}",
                        subject, e.getMessage());
            }
        }

        natsConnection.publish(subject, data);
        log.debug("Published raw data to core NATS subject {}", subject);
    }
}
