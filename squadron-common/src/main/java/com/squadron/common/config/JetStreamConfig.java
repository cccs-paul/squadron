package com.squadron.common.config;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;

@Configuration
@ConditionalOnBean(Connection.class)
public class JetStreamConfig {

    private static final Logger log = LoggerFactory.getLogger(JetStreamConfig.class);

    private final Connection natsConnection;

    public JetStreamConfig(Connection natsConnection) {
        this.natsConnection = natsConnection;
    }

    @Bean
    public JetStream jetStream() throws IOException {
        return natsConnection.jetStream();
    }

    @Bean
    public JetStreamManagement jetStreamManagement() throws IOException {
        return natsConnection.jetStreamManagement();
    }

    @PostConstruct
    public void createStreams() {
        try {
            JetStreamManagement jsm = natsConnection.jetStreamManagement();

            createOrUpdateStream(jsm, "TASKS", "squadron.tasks.>", Duration.ofDays(7));
            createOrUpdateStream(jsm, "AGENTS", "squadron.agents.>", Duration.ofDays(7));
            createOrUpdateStream(jsm, "AGENT_EVENTS", "squadron.agent.>", Duration.ofDays(7));
            createOrUpdateStream(jsm, "WORKSPACES", "squadron.workspaces.>", Duration.ofDays(3));
            createOrUpdateStream(jsm, "REVIEWS", "squadron.reviews.>", Duration.ofDays(7));
            createOrUpdateStream(jsm, "NOTIFICATIONS", "squadron.notifications.>", Duration.ofDays(3));
            createOrUpdateStream(jsm, "CONFIG", "squadron.config.>", Duration.ofDays(1));
            createOrUpdateStream(jsm, "GIT_EVENTS", "squadron.git.>", Duration.ofDays(7));
            createOrUpdateStream(jsm, "PLATFORM", "platform.>", Duration.ofDays(3));
            createOrUpdateStream(jsm, "DLQ", "squadron.dlq.>", Duration.ofDays(30));

            log.info("JetStream streams initialized successfully");
        } catch (Exception e) {
            log.warn("Failed to initialize JetStream streams (NATS may not support JetStream): {}", e.getMessage());
        }
    }

    void createOrUpdateStream(JetStreamManagement jsm, String name, String subject, Duration maxAge) {
        try {
            StreamConfiguration config = StreamConfiguration.builder()
                    .name(name)
                    .subjects(subject)
                    .retentionPolicy(RetentionPolicy.Limits)
                    .storageType(StorageType.File)
                    .maxAge(maxAge)
                    .replicas(1)
                    .build();

            try {
                StreamInfo info = jsm.getStreamInfo(name);
                jsm.updateStream(config);
                log.debug("Updated JetStream stream: {}", name);
            } catch (Exception e) {
                jsm.addStream(config);
                log.info("Created JetStream stream: {}", name);
            }
        } catch (Exception e) {
            log.warn("Failed to create/update JetStream stream {}: {}", name, e.getMessage());
        }
    }
}
