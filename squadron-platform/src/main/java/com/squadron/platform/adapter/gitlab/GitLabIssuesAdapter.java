package com.squadron.platform.adapter.gitlab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.platform.adapter.TicketingPlatformAdapter;
import com.squadron.platform.dto.PlatformTaskDto;
import com.squadron.platform.dto.PlatformTaskFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Adapter for GitLab Issues using the GitLab REST API v4.
 * API base: {baseUrl}/api/v4/
 *
 * The projectKey is expected to be the GitLab project ID (numeric).
 * The externalId format is "projectId:issueIid" (e.g., "42:7").
 */
@Component
public class GitLabIssuesAdapter implements TicketingPlatformAdapter {

    private static final Logger log = LoggerFactory.getLogger(GitLabIssuesAdapter.class);
    private static final String PLATFORM_TYPE = "GITLAB";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private WebClient webClient;
    private String baseUrl;
    private String accessToken;

    public GitLabIssuesAdapter(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    public void configure(String baseUrl, String accessToken) {
        this.baseUrl = baseUrl;
        this.accessToken = accessToken;
        this.webClient = webClientBuilder
                .baseUrl(baseUrl + "/api/v4")
                .defaultHeader("PRIVATE-TOKEN", accessToken)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("Configured GitLab Issues adapter for {}", baseUrl);
    }

    @Override
    public List<PlatformTaskDto> fetchTasks(String projectKey, PlatformTaskFilter filter) {
        log.info("Fetching issues from GitLab for project {}", projectKey);

        StringBuilder uri = new StringBuilder("/projects/" + projectKey + "/issues?");

        int maxResults = 50;
        if (filter != null) {
            if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                uri.append("state=").append(filter.getStatus()).append("&");
            }
            if (filter.getAssignee() != null && !filter.getAssignee().isBlank()) {
                uri.append("assignee_username=").append(filter.getAssignee()).append("&");
            }
            if (filter.getMaxResults() != null) {
                maxResults = filter.getMaxResults();
            }
        }
        uri.append("per_page=").append(maxResults);

        String responseBody = webClient.get()
                .uri(uri.toString())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            List<Map<String, Object>> issues = objectMapper.readValue(
                    responseBody, new TypeReference<>() {});

            List<PlatformTaskDto> tasks = new ArrayList<>();
            for (Map<String, Object> issue : issues) {
                tasks.add(mapIssueToDto(projectKey, issue));
            }
            return tasks;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse GitLab issues response", e);
        }
    }

    @Override
    public PlatformTaskDto getTask(String externalId) {
        log.info("Getting issue {} from GitLab", externalId);

        String[] parts = parseExternalId(externalId);
        String projectId = parts[0];
        String issueIid = parts[1];

        String uri = "/projects/" + projectId + "/issues/" + issueIid;

        String responseBody = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            Map<String, Object> issue = objectMapper.readValue(
                    responseBody, new TypeReference<>() {});
            return mapIssueToDto(projectId, issue);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse GitLab issue response", e);
        }
    }

    @Override
    public void updateTaskStatus(String externalId, String status, String comment) {
        log.info("Updating issue {} status to {} on GitLab", externalId, status);

        String[] parts = parseExternalId(externalId);
        String projectId = parts[0];
        String issueIid = parts[1];

        String stateEvent;
        if ("closed".equalsIgnoreCase(status) || "close".equalsIgnoreCase(status)) {
            stateEvent = "close";
        } else {
            stateEvent = "reopen";
        }

        String uri = "/projects/" + projectId + "/issues/" + issueIid;

        webClient.put()
                .uri(uri)
                .bodyValue(Map.of("state_event", stateEvent))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (comment != null && !comment.isBlank()) {
            addComment(externalId, comment);
        }
    }

    @Override
    public void addComment(String externalId, String comment) {
        log.info("Adding comment to issue {} on GitLab", externalId);

        String[] parts = parseExternalId(externalId);
        String projectId = parts[0];
        String issueIid = parts[1];

        String uri = "/projects/" + projectId + "/issues/" + issueIid + "/notes";

        webClient.post()
                .uri(uri)
                .bodyValue(Map.of("body", comment))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @Override
    public List<String> getAvailableStatuses(String projectKey) {
        log.info("Getting available statuses for GitLab project {}", projectKey);
        // GitLab issues have "opened" and "closed" states
        return List.of("opened", "closed");
    }

    @Override
    public boolean testConnection() {
        log.info("Testing GitLab connection to {}", baseUrl);
        if (webClient == null) {
            log.warn("GitLab adapter not configured");
            return false;
        }
        try {
            webClient.get()
                    .uri("/user")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("GitLab connection test successful");
            return true;
        } catch (Exception e) {
            log.error("GitLab connection test failed", e);
            return false;
        }
    }

    /**
     * Parses the external ID format "projectId:issueIid" into its components.
     *
     * @param externalId the external ID in format "projectId:issueIid"
     * @return a String array where [0] is the project ID and [1] is the issue IID
     * @throws IllegalArgumentException if the format is invalid
     */
    String[] parseExternalId(String externalId) {
        if (externalId == null || !externalId.contains(":")) {
            throw new IllegalArgumentException(
                    "Invalid GitLab external ID format. Expected 'projectId:issueIid', got: " + externalId);
        }
        String[] parts = externalId.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid GitLab external ID format. Expected 'projectId:issueIid', got: " + externalId);
        }
        return parts;
    }

    @SuppressWarnings("unchecked")
    private PlatformTaskDto mapIssueToDto(String projectId, Map<String, Object> issue) {
        Number iid = (Number) issue.get("iid");
        String externalId = projectId + ":" + iid.intValue();

        String assigneeUsername = null;
        Object assigneeObj = issue.get("assignee");
        if (assigneeObj instanceof Map) {
            Map<String, Object> assignee = (Map<String, Object>) assigneeObj;
            assigneeUsername = (String) assignee.get("username");
        }

        List<String> labels;
        Object labelsObj = issue.get("labels");
        if (labelsObj instanceof List) {
            labels = (List<String>) labelsObj;
        } else {
            labels = Collections.emptyList();
        }

        // GitLab doesn't have a built-in priority field; check for priority-related labels
        String priority = extractPriorityFromLabels(labels);

        Instant createdAt = issue.get("created_at") != null
                ? Instant.parse((String) issue.get("created_at")) : null;
        Instant updatedAt = issue.get("updated_at") != null
                ? Instant.parse((String) issue.get("updated_at")) : null;

        return PlatformTaskDto.builder()
                .externalId(externalId)
                .externalUrl((String) issue.get("web_url"))
                .title((String) issue.get("title"))
                .description((String) issue.get("description"))
                .status((String) issue.get("state"))
                .priority(priority)
                .assignee(assigneeUsername)
                .labels(labels)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    private String extractPriorityFromLabels(List<String> labels) {
        if (labels == null) {
            return null;
        }
        for (String label : labels) {
            String lower = label.toLowerCase();
            if (lower.startsWith("priority:") || lower.startsWith("priority::")) {
                return label.substring(label.lastIndexOf(':') + 1).trim();
            }
            if (lower.equals("critical") || lower.equals("high")
                    || lower.equals("medium") || lower.equals("low")) {
                return label;
            }
        }
        return null;
    }
}
