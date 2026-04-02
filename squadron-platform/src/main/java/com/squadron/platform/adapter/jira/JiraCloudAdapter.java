package com.squadron.platform.adapter.jira;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.platform.adapter.TicketingPlatformAdapter;
import com.squadron.platform.config.AdapterErrorHelper;
import com.squadron.platform.config.WebClientSslHelper;
import com.squadron.platform.dto.PlatformProjectDto;
import com.squadron.platform.dto.PlatformTaskDto;
import com.squadron.platform.dto.PlatformTaskFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter for Jira Cloud using the Atlassian REST API v3.
 * API base: https://{domain}.atlassian.net/rest/api/3/
 */
@Component
public class JiraCloudAdapter implements TicketingPlatformAdapter {

    private static final Logger log = LoggerFactory.getLogger(JiraCloudAdapter.class);
    private static final String PLATFORM_TYPE = "JIRA_CLOUD";
    private static final String SEARCH_FIELDS = "summary,description,status,priority,assignee,labels,created,updated";

    private final WebClientSslHelper sslHelper;
    private final ObjectMapper objectMapper;
    private WebClient webClient;
    private String baseUrl;
    private String accessToken;

    public JiraCloudAdapter(WebClientSslHelper sslHelper) {
        this.sslHelper = sslHelper;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    public void configure(String baseUrl, Map<String, String> credentials) {
        this.baseUrl = baseUrl;
        this.accessToken = resolveToken(credentials);

        // Determine the correct Authorization header based on available credentials.
        // Jira Cloud API Token auth requires Basic auth: base64(email:apiToken)
        // OAuth 2.0 uses Bearer token.
        String authHeader;
        String email = credentials.get("email");
        String apiToken = credentials.get("apiToken");
        if (email != null && !email.isEmpty() && apiToken != null && !apiToken.isEmpty()) {
            String encoded = Base64.getEncoder().encodeToString(
                    (email + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
            authHeader = "Basic " + encoded;
        } else {
            // Fall back to Bearer for OAuth / accessToken / PAT flows
            authHeader = "Bearer " + this.accessToken;
        }

        this.webClient = sslHelper.trustedBuilder()
                .baseUrl(baseUrl + "/rest/api/3")
                .defaultHeader("Authorization", authHeader)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("Configured Jira Cloud adapter for {}", baseUrl);
    }

    @Override
    public List<PlatformTaskDto> fetchTasks(String projectKey, PlatformTaskFilter filter) {
        log.info("Fetching tasks from Jira Cloud for project {}", projectKey);
        try {
            StringBuilder jql = new StringBuilder("project = " + projectKey);
            if (filter != null && filter.getStatus() != null && !filter.getStatus().isBlank()) {
                jql.append(" AND status = \"").append(filter.getStatus()).append("\"");
            }
            if (filter != null && filter.getAssignee() != null && !filter.getAssignee().isBlank()) {
                jql.append(" AND assignee = \"").append(filter.getAssignee()).append("\"");
            }
            int maxResults = (filter != null && filter.getMaxResults() != null) ? filter.getMaxResults() : 50;

            String encodedJql = URLEncoder.encode(jql.toString(), StandardCharsets.UTF_8);
            String uri = "/search?jql=" + encodedJql + "&maxResults=" + maxResults + "&fields=" + SEARCH_FIELDS;

            String responseBody = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String htmlError = AdapterErrorHelper.checkForHtmlResponse(responseBody, log);
            if (htmlError != null) {
                throw new RuntimeException("Failed to fetch tasks from Jira Cloud: " + htmlError);
            }

            Map<String, Object> responseMap = objectMapper.readValue(responseBody, new TypeReference<>() {});
            List<Map<String, Object>> issues = castToListOfMaps(responseMap.get("issues"));
            if (issues == null) {
                return Collections.emptyList();
            }

            List<PlatformTaskDto> result = new ArrayList<>();
            for (Map<String, Object> issue : issues) {
                result.add(mapIssueToPlatformTask(issue));
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to fetch tasks from Jira Cloud for project {}: {}", projectKey, message, e);
            throw new RuntimeException("Failed to fetch tasks from Jira Cloud: " + message, e);
        }
    }

    @Override
    public PlatformTaskDto getTask(String externalId) {
        log.info("Getting task {} from Jira Cloud", externalId);
        try {
            String uri = "/issue/" + externalId + "?fields=" + SEARCH_FIELDS;

            String responseBody = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String htmlError = AdapterErrorHelper.checkForHtmlResponse(responseBody, log);
            if (htmlError != null) {
                throw new RuntimeException("Failed to get task from Jira Cloud: " + htmlError);
            }

            Map<String, Object> issue = objectMapper.readValue(responseBody, new TypeReference<>() {});
            return mapIssueToPlatformTask(issue);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to get task {} from Jira Cloud: {}", externalId, message, e);
            throw new RuntimeException("Failed to get task from Jira Cloud: " + message, e);
        }
    }

    @Override
    public void updateTaskStatus(String externalId, String status, String comment) {
        log.info("Updating task {} status to {} on Jira Cloud", externalId, status);
        try {
            // Step 1: Get available transitions
            String transitionsUri = "/issue/" + externalId + "/transitions";
            String transitionsBody = webClient.get()
                    .uri(transitionsUri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Map<String, Object> transitionsMap = objectMapper.readValue(transitionsBody, new TypeReference<>() {});
            List<Map<String, Object>> transitions = castToListOfMaps(transitionsMap.get("transitions"));
            if (transitions == null || transitions.isEmpty()) {
                throw new RuntimeException("No transitions available for issue " + externalId);
            }

            // Step 2: Find matching transition
            String transitionId = null;
            for (Map<String, Object> transition : transitions) {
                String transitionName = (String) transition.get("name");
                if (transitionName != null && transitionName.equalsIgnoreCase(status)) {
                    transitionId = String.valueOf(transition.get("id"));
                    break;
                }
            }
            if (transitionId == null) {
                throw new RuntimeException("No transition found matching status '" + status + "' for issue " + externalId);
            }

            // Step 3: Execute the transition
            Map<String, Object> transitionBody = Map.of("transition", Map.of("id", transitionId));
            webClient.post()
                    .uri(transitionsUri)
                    .bodyValue(transitionBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Step 4: Add comment if provided
            if (comment != null && !comment.isBlank()) {
                addComment(externalId, comment);
            }

            log.info("Successfully updated task {} status to {}", externalId, status);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to update task {} status on Jira Cloud: {}", externalId, message, e);
            throw new RuntimeException("Failed to update task status on Jira Cloud: " + message, e);
        }
    }

    @Override
    public void addComment(String externalId, String comment) {
        log.info("Adding comment to task {} on Jira Cloud", externalId);
        try {
            // Atlassian Document Format (ADF) body for v3
            Map<String, Object> body = Map.of(
                    "body", Map.of(
                            "type", "doc",
                            "version", 1,
                            "content", List.of(
                                    Map.of(
                                            "type", "paragraph",
                                            "content", List.of(
                                                    Map.of("type", "text", "text", comment)
                                            )
                                    )
                            )
                    )
            );

            String uri = "/issue/" + externalId + "/comment";
            webClient.post()
                    .uri(uri)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Successfully added comment to task {}", externalId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to add comment to task {} on Jira Cloud: {}", externalId, message, e);
            throw new RuntimeException("Failed to add comment on Jira Cloud: " + message, e);
        }
    }

    @Override
    public List<String> getAvailableStatuses(String projectKey) {
        log.info("Getting available statuses for project {} from Jira Cloud", projectKey);
        try {
            String uri = "/project/" + projectKey + "/statuses";
            String responseBody = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String htmlError = AdapterErrorHelper.checkForHtmlResponse(responseBody, log);
            if (htmlError != null) {
                throw new RuntimeException("Failed to get available statuses from Jira Cloud: " + htmlError);
            }

            List<Map<String, Object>> issueTypes = objectMapper.readValue(responseBody, new TypeReference<>() {});
            Set<String> statusNames = new LinkedHashSet<>();
            for (Map<String, Object> issueType : issueTypes) {
                List<Map<String, Object>> statuses = castToListOfMaps(issueType.get("statuses"));
                if (statuses != null) {
                    for (Map<String, Object> statusObj : statuses) {
                        String name = (String) statusObj.get("name");
                        if (name != null) {
                            statusNames.add(name);
                        }
                    }
                }
            }
            return new ArrayList<>(statusNames);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to get available statuses for project {} from Jira Cloud: {}", projectKey, message, e);
            throw new RuntimeException("Failed to get available statuses from Jira Cloud: " + message, e);
        }
    }

    @Override
    public boolean testConnection() {
        log.info("Testing Jira Cloud connection to {}", baseUrl);
        if (webClient == null) {
            log.warn("Jira Cloud adapter not configured");
            return false;
        }
        try {
            webClient.get()
                    .uri("/myself")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("Jira Cloud connection test successful");
            return true;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Jira Cloud connection test failed: {}", message, e);
            return false;
        }
    }

    @Override
    public List<PlatformProjectDto> getProjects() {
        log.info("Fetching projects from Jira Cloud");
        try {
            String responseBody = webClient.get()
                    .uri("/project")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String htmlError = AdapterErrorHelper.checkForHtmlResponse(responseBody, log);
            if (htmlError != null) {
                throw new RuntimeException("Failed to fetch projects from Jira Cloud: " + htmlError);
            }

            List<Map<String, Object>> projects = objectMapper.readValue(
                    responseBody, new TypeReference<List<Map<String, Object>>>() {});

            List<PlatformProjectDto> result = new ArrayList<>();
            for (Map<String, Object> project : projects) {
                String key = (String) project.get("key");
                String name = (String) project.get("name");

                // Extract description — may be null or ADF object
                String description = null;
                Object descObj = project.get("description");
                if (descObj instanceof String) {
                    description = (String) descObj;
                } else if (descObj != null) {
                    try {
                        description = objectMapper.writeValueAsString(descObj);
                    } catch (Exception ignored) {
                        description = descObj.toString();
                    }
                }

                // Extract avatar URL from avatarUrls map
                String avatarUrl = null;
                Map<String, Object> avatarUrls = castToMap(project.get("avatarUrls"));
                if (avatarUrls != null) {
                    avatarUrl = (String) avatarUrls.get("48x48");
                }

                String url = baseUrl + "/browse/" + key;

                result.add(PlatformProjectDto.builder()
                        .key(key)
                        .name(name)
                        .description(description)
                        .url(url)
                        .avatarUrl(avatarUrl)
                        .build());
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to fetch projects from Jira Cloud: {}", message, e);
            throw new RuntimeException("Failed to fetch projects from Jira Cloud: " + message, e);
        }
    }

    // --- Helper methods ---

    private PlatformTaskDto mapIssueToPlatformTask(Map<String, Object> issue) {
        String key = (String) issue.get("key");
        Map<String, Object> fields = castToMap(issue.get("fields"));

        String summary = fields != null ? (String) fields.get("summary") : null;
        String description = extractDescription(fields);
        String statusName = extractNestedName(fields, "status");
        String priorityName = extractNestedName(fields, "priority");
        String assigneeDisplayName = extractAssigneeDisplayName(fields);
        List<String> labels = extractLabels(fields);
        Instant createdAt = parseInstant(fields != null ? (String) fields.get("created") : null);
        Instant updatedAt = parseInstant(fields != null ? (String) fields.get("updated") : null);

        String externalUrl = baseUrl + "/browse/" + key;

        return PlatformTaskDto.builder()
                .externalId(key)
                .externalUrl(externalUrl)
                .title(summary)
                .description(description)
                .status(statusName)
                .priority(priorityName)
                .assignee(assigneeDisplayName)
                .labels(labels)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    private String extractDescription(Map<String, Object> fields) {
        if (fields == null) return null;
        Object descObj = fields.get("description");
        if (descObj == null) return null;
        if (descObj instanceof String) return (String) descObj;
        // ADF format — serialize to JSON string for preservation
        try {
            return objectMapper.writeValueAsString(descObj);
        } catch (Exception e) {
            return descObj.toString();
        }
    }

    private String extractNestedName(Map<String, Object> fields, String fieldName) {
        if (fields == null) return null;
        Map<String, Object> nested = castToMap(fields.get(fieldName));
        return nested != null ? (String) nested.get("name") : null;
    }

    private String extractAssigneeDisplayName(Map<String, Object> fields) {
        if (fields == null) return null;
        Map<String, Object> assignee = castToMap(fields.get("assignee"));
        return assignee != null ? (String) assignee.get("displayName") : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractLabels(Map<String, Object> fields) {
        if (fields == null) return Collections.emptyList();
        Object labelsObj = fields.get("labels");
        if (labelsObj instanceof List) {
            return (List<String>) labelsObj;
        }
        return Collections.emptyList();
    }

    private Instant parseInstant(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            // Jira uses format like "2024-01-01T00:00:00.000+0000" where the offset
            // lacks a colon. Normalize "+0000" to "+00:00" for ISO-8601 compliance.
            String normalized = dateStr.replaceAll("([+-])(\\d{2})(\\d{2})$", "$1$2:$3");
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(normalized, Instant::from);
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(dateStr);
            } catch (DateTimeParseException e2) {
                log.warn("Unable to parse date: {}", dateStr);
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castToListOfMaps(Object obj) {
        if (obj instanceof List) {
            return (List<Map<String, Object>>) obj;
        }
        return null;
    }

    /**
     * Extracts a single token value from the credentials map by checking known token field names.
     * Used as a fallback for OAuth/Bearer flows where a single token is sufficient.
     */
    private String resolveToken(Map<String, String> credentials) {
        for (String key : List.of("accessToken", "pat", "apiKey", "apiToken")) {
            String value = credentials.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }
}
