package com.squadron.agent.tool.builtin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST client for communicating with the squadron-workspace service.
 * Provides methods to execute commands, read files, and write files
 * inside workspace containers.
 */
@Service
public class WorkspaceClient {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceClient.class);

    private final WebClient webClient;

    @Autowired
    public WorkspaceClient(@Value("${squadron.workspace.url:http://localhost:8085}") String workspaceUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(workspaceUrl)
                .build();
    }

    /**
     * Constructor for testing — accepts a pre-built WebClient.
     */
    WorkspaceClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Executes a command inside the workspace container.
     *
     * @param workspaceId the workspace container ID
     * @param command     the command parts (e.g. "bash", "-c", "cat /workspace/file.txt")
     * @return the execution result containing exit code, stdout, and stderr
     */
    public ExecResultDto exec(UUID workspaceId, String... command) {
        log.debug("Executing command in workspace {}: {}", workspaceId, List.of(command));

        Map<String, Object> requestBody = Map.of(
                "workspaceId", workspaceId.toString(),
                "command", List.of(command)
        );

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/api/workspaces/{workspaceId}/exec", workspaceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null || response.get("data") == null) {
                throw new WorkspaceClientException("Empty response from workspace service");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");

            return ExecResultDto.builder()
                    .exitCode(data.get("exitCode") instanceof Number n ? n.intValue() : 0)
                    .stdout(data.get("stdout") != null ? data.get("stdout").toString() : "")
                    .stderr(data.get("stderr") != null ? data.get("stderr").toString() : "")
                    .build();
        } catch (WebClientResponseException e) {
            log.error("Workspace exec failed for workspace {}: {} {}", workspaceId,
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new WorkspaceClientException(
                    "Workspace exec failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Writes a file to the workspace container.
     *
     * @param workspaceId the workspace container ID
     * @param path        the absolute path inside the container
     * @param content     the file content as bytes
     */
    public void writeFile(UUID workspaceId, String path, byte[] content) {
        log.debug("Writing file to workspace {}: {} ({} bytes)", workspaceId, path, content.length);

        try {
            webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/workspaces/{workspaceId}/copy-to")
                            .queryParam("path", path)
                            .build(workspaceId))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .bodyValue(content)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Workspace writeFile failed for workspace {}: {} {}", workspaceId,
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new WorkspaceClientException(
                    "Workspace writeFile failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Reads a file from the workspace container.
     *
     * @param workspaceId the workspace container ID
     * @param path        the absolute path inside the container
     * @return the file content as bytes
     */
    public byte[] readFile(UUID workspaceId, String path) {
        log.debug("Reading file from workspace {}: {}", workspaceId, path);

        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/workspaces/{workspaceId}/copy-from")
                            .queryParam("path", path)
                            .build(workspaceId))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Workspace readFile failed for workspace {}: {} {}", workspaceId,
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new WorkspaceClientException(
                    "Workspace readFile failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Exception thrown when workspace service communication fails.
     */
    public static class WorkspaceClientException extends RuntimeException {
        public WorkspaceClientException(String message) {
            super(message);
        }

        public WorkspaceClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
