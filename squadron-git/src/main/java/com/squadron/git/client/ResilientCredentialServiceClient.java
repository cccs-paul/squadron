package com.squadron.git.client;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.dto.CredentialResolutionResult;
import com.squadron.common.dto.ResolveCredentialRequest;
import com.squadron.common.resilience.ResilientClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Resilient wrapper around {@link CredentialServiceClient} that adds circuit breaker
 * and retry logic using the custom resilience stack.
 */
@Service
@Slf4j
public class ResilientCredentialServiceClient {

    private final CredentialServiceClient credentialServiceClient;
    @Getter
    private final ResilientClient resilientClient;

    @Autowired
    public ResilientCredentialServiceClient(CredentialServiceClient credentialServiceClient) {
        this.credentialServiceClient = credentialServiceClient;
        this.resilientClient = ResilientClient.withDefaults("credential-service");
        log.info("Initialized resilient credential service client with circuit breaker");
    }

    /**
     * Constructor for testing with a custom ResilientClient.
     */
    ResilientCredentialServiceClient(CredentialServiceClient credentialServiceClient, ResilientClient resilientClient) {
        this.credentialServiceClient = credentialServiceClient;
        this.resilientClient = resilientClient;
    }

    public ApiResponse<CredentialResolutionResult> resolveCredentials(ResolveCredentialRequest request) {
        return resilientClient.execute("resolveCredentials",
                () -> credentialServiceClient.resolveCredentials(request));
    }
}
