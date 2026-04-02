package com.squadron.workspace.client;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.resilience.ResilientClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
    ResilientPlatformServiceClient(PlatformServiceClient delegate, ResilientClient resilientClient) {
        this.delegate = delegate;
        this.resilientClient = resilientClient;
    }

    /**
     * Retrieves the decrypted SSH private key for the given key ID.
     */
    public String getDecryptedPrivateKey(UUID sshKeyId) {
        ApiResponse<String> response = resilientClient.execute("getDecryptedPrivateKey",
                () -> delegate.getDecryptedPrivateKey(sshKeyId));
        return response.getData();
    }

    public ResilientClient getResilientClient() {
        return resilientClient;
    }
}
