package com.squadron.agent.client;

import com.squadron.common.resilience.ResilientClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Resilient wrapper around {@link OrchestratorClient} that adds circuit breaker
 * and retry logic using the custom resilience stack.
 */
@Slf4j
@Service
public class ResilientOrchestratorClient {

    private final OrchestratorClient delegate;
    private final ResilientClient resilientClient;

    public ResilientOrchestratorClient(OrchestratorClient delegate) {
        this.delegate = delegate;
        this.resilientClient = ResilientClient.withDefaults("orchestrator");
        log.info("Initialized resilient orchestrator client with circuit breaker");
    }

    /**
     * Constructor for testing with a custom ResilientClient.
     */
    ResilientOrchestratorClient(OrchestratorClient delegate, ResilientClient resilientClient) {
        this.delegate = delegate;
        this.resilientClient = resilientClient;
    }

    public Map<String, Object> transitionTask(String taskId, Map<String, Object> request) {
        return resilientClient.execute("transitionTask",
                () -> delegate.transitionTask(taskId, request));
    }

    public ResilientClient getResilientClient() {
        return resilientClient;
    }
}
