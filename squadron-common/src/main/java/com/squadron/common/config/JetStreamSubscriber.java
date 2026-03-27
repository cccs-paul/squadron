package com.squadron.common.config;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Consumer;

@Component
public class JetStreamSubscriber {

    private static final Logger log = LoggerFactory.getLogger(JetStreamSubscriber.class);

    private final Connection natsConnection;
    private JetStream jetStream;

    public JetStreamSubscriber(Connection natsConnection) {
        this.natsConnection = natsConnection;
    }

    @Autowired(required = false)
    public void setJetStream(JetStream jetStream) {
        this.jetStream = jetStream;
    }

    /**
     * Subscribe to a subject with a durable consumer name and queue group.
     * Falls back to plain NATS dispatcher if JetStream is unavailable.
     */
    public void subscribe(String subject, String durableName, String queueGroup, Consumer<Message> handler) {
        if (jetStream != null) {
            try {
                PushSubscribeOptions options = PushSubscribeOptions.builder()
                        .durable(durableName)
                        .configuration(ConsumerConfiguration.builder()
                                .deliverPolicy(DeliverPolicy.All)
                                .ackWait(Duration.ofSeconds(30))
                                .maxDeliver(5)
                                .build())
                        .build();

                Dispatcher dispatcher = natsConnection.createDispatcher();
                jetStream.subscribe(subject, queueGroup, dispatcher, msg -> {
                    try {
                        handler.accept(msg);
                        msg.ack();
                    } catch (Exception e) {
                        log.error("Failed to process JetStream message on {}: {}", subject, e.getMessage());
                        msg.nak();
                    }
                }, false, options);

                log.info("JetStream durable subscription created: subject={}, durable={}, queue={}",
                        subject, durableName, queueGroup);
                return;
            } catch (Exception e) {
                log.warn("Failed to create JetStream subscription for {}, falling back to core NATS: {}",
                        subject, e.getMessage());
            }
        }

        // Fallback to plain NATS
        Dispatcher dispatcher = natsConnection.createDispatcher(msg -> {
            try {
                handler.accept(msg);
            } catch (Exception e) {
                log.error("Failed to process NATS message on {}: {}", subject, e.getMessage());
            }
        });
        dispatcher.subscribe(subject, queueGroup);
        log.info("Core NATS subscription created (no durability): subject={}, queue={}", subject, queueGroup);
    }

    /**
     * Subscribe without a queue group (all instances receive all messages).
     */
    public void subscribe(String subject, String durableName, Consumer<Message> handler) {
        subscribe(subject, durableName, null, handler);
    }
}
