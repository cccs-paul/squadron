package com.squadron.orchestrator.client;

import com.squadron.common.resilience.ResilientClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Resilient wrapper around {@link PlatformServiceClient} that adds circuit breaker
 * and retry logic using the custom resilience stack.
 */
@Slf4j
@Service
public class ResilientPlatformServiceClient {

    private final PlatformServiceClient delegate;
    private final ResilientClient resilientClient;

    @Autowired
    public ResilientPlatformServiceClient(PlatformServiceClient delegate) {
        this.delegate = delegate;
        this.resilientClient = ResilientClient.withDefaults("platform-service");
        log.info("Initialized resilient platform service client with circuit breaker");
    }

    /**
     * Constructor for testing with a custom ResilientClient.
     */
    @SuppressWarnings("unused")
    private ResilientPlatformServiceClient(PlatformServiceClient delegate, ResilientClient resilientClient) {
        this.delegate = delegate;
        this.resilientClient = resilientClient;
    }

    public List<Map<String, Object>> fetchTasks(String connectionId, String projectKey) {
        return resilientClient.execute("fetchTasks",
                () -> delegate.fetchTasks(connectionId, projectKey));
    }

    public ResilientClient getResilientClient() {
        return resilientClient;
    }
}
