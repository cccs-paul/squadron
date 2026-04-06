package com.squadron.agent.tool.builtin;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.dto.CredentialResolutionResult;
import com.squadron.common.dto.ResolveCredentialRequest;
import com.squadron.common.security.CredentialPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

/**
 * WebClient-based credential resolution client.
 * Calls squadron-platform's credential resolution endpoint.
 */
@Component
public class CredentialClient {

    private static final Logger log = LoggerFactory.getLogger(CredentialClient.class);
    private final WebClient webClient;

    @Autowired
    public CredentialClient(@Value("${squadron.platform.service-url:http://localhost:8084}") String platformServiceUrl,
                            WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(platformServiceUrl).build();
    }

    // Constructor for testing
    CredentialClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Resolves credentials for a user on a specific platform connection.
     *
     * @param userId       the user whose credentials to resolve
     * @param connectionId the platform connection ID
     * @param purpose      the purpose for which credentials are needed
     * @return the resolved credential result
     * @throws RuntimeException if credential resolution fails
     */
    public CredentialResolutionResult resolveCredentials(UUID userId, UUID connectionId, CredentialPurpose purpose) {
        log.debug("Resolving credentials for user {} on connection {} for purpose {}", userId, connectionId, purpose);

        ResolveCredentialRequest request = ResolveCredentialRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .purpose(purpose)
                .build();

        try {
            ApiResponse<CredentialResolutionResult> response = webClient.post()
                    .uri("/api/platforms/credentials/resolve")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiResponse<CredentialResolutionResult>>() {})
                    .block();

            if (response != null && response.isSuccess() && response.getData() != null) {
                return response.getData();
            }

            throw new RuntimeException("Credential resolution returned unsuccessful response");
        } catch (RuntimeException e) {
            log.error("Failed to resolve credentials for user {} on connection {}: {}", userId, connectionId, e.getMessage());
            throw new RuntimeException("Credential resolution failed: " + e.getMessage(), e);
        }
    }
}
