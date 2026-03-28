package com.squadron.agent.client;

import com.squadron.common.resilience.ResilientClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Resilient wrapper around {@link WorkspaceServiceClient} that adds circuit breaker
 * and retry logic using the custom resilience stack.
 */
@Slf4j
@Service
public class ResilientWorkspaceServiceClient {

    private final WorkspaceServiceClient delegate;
    private final ResilientClient resilientClient;

    @Autowired
    public ResilientWorkspaceServiceClient(WorkspaceServiceClient delegate) {
        this.delegate = delegate;
        this.resilientClient = ResilientClient.withDefaults("workspace-service");
        log.info("Initialized resilient workspace service client with circuit breaker");
    }

    /**
     * Constructor for testing with a custom ResilientClient.
     */
    ResilientWorkspaceServiceClient(WorkspaceServiceClient delegate, ResilientClient resilientClient) {
        this.delegate = delegate;
        this.resilientClient = resilientClient;
    }

    public Map<String, Object> exec(String workspaceId, Map<String, Object> request) {
        return resilientClient.execute("exec",
                () -> delegate.exec(workspaceId, request));
    }

    public void writeFile(String workspaceId, String path, byte[] content) {
        resilientClient.executeVoid("writeFile",
                () -> delegate.writeFile(workspaceId, path, content));
    }

    public byte[] readFile(String workspaceId, String path) {
        return resilientClient.execute("readFile",
                () -> delegate.readFile(workspaceId, path));
    }

    public ResilientClient getResilientClient() {
        return resilientClient;
    }
}
