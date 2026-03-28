package com.squadron.agent.client;

import com.squadron.common.resilience.ResilientClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Resilient wrapper around {@link ReviewServiceClient} that adds circuit breaker
 * and retry logic using the custom resilience stack.
 */
@Slf4j
@Service
public class ResilientReviewServiceClient {

    private final ReviewServiceClient delegate;
    private final ResilientClient resilientClient;

    @Autowired
    public ResilientReviewServiceClient(ReviewServiceClient delegate) {
        this.delegate = delegate;
        this.resilientClient = ResilientClient.withDefaults("review-service");
        log.info("Initialized resilient review service client with circuit breaker");
    }

    /**
     * Constructor for testing with a custom ResilientClient.
     */
    ResilientReviewServiceClient(ReviewServiceClient delegate, ResilientClient resilientClient) {
        this.delegate = delegate;
        this.resilientClient = resilientClient;
    }

    public Map<String, Object> createReview(Map<String, Object> request) {
        return resilientClient.execute("createReview",
                () -> delegate.createReview(request));
    }

    public Map<String, Object> submitReview(Map<String, Object> request) {
        return resilientClient.execute("submitReview",
                () -> delegate.submitReview(request));
    }

    public Map<String, Object> checkReviewGate(String taskId, String tenantId, String teamId) {
        return resilientClient.execute("checkReviewGate",
                () -> delegate.checkReviewGate(taskId, tenantId, teamId));
    }

    public ResilientClient getResilientClient() {
        return resilientClient;
    }
}
