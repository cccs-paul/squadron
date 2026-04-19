package com.squadron.common.config;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;

@Configuration
public class NatsConfig {

    private static final Logger log = LoggerFactory.getLogger(NatsConfig.class);

    @Value("${squadron.nats.url:nats://localhost:4222}")
    private String natsUrl;

    @Bean(destroyMethod = "close")
    public Connection natsConnection() throws IOException, InterruptedException {
        Options options = new Options.Builder()
                .server(natsUrl)
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(2))
                .connectionTimeout(Duration.ofSeconds(5))
                .build();

        int maxAttempts = 15;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Connection conn = Nats.connect(options);
                log.info("Connected to NATS at {} (attempt {})", natsUrl, attempt);
                return conn;
            } catch (IOException e) {
                if (attempt == maxAttempts) {
                    log.error("Failed to connect to NATS at {} after {} attempts", natsUrl, maxAttempts);
                    throw e;
                }
                log.warn("NATS connection attempt {}/{} failed: {}. Retrying in 3s...",
                        attempt, maxAttempts, e.getMessage());
                Thread.sleep(3000);
            }
        }
        throw new IOException("Failed to connect to NATS at " + natsUrl);
    }
}
