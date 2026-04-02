package com.squadron.platform.adapter.azuredevops;

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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter for Azure DevOps Work Items using the Azure DevOps REST API.
 * API base: https://dev.azure.com/{org}/{project}/_apis/
 *
 * The projectKey is expected in the format "organization/project".
 */
@Component
public class AzureDevOpsAdapter implements TicketingPlatformAdapter {

    private static final Logger log = LoggerFactory.getLogger(AzureDevOpsAdapter.class);
    private static final String PLATFORM_TYPE = "AZURE_DEVOPS";

    private final WebClientSslHelper sslHelper;
    private final ObjectMapper objectMapper;
    private WebClient webClient;
    private String baseUrl;
    private String accessToken;

    public AzureDevOpsAdapter(WebClientSslHelper sslHelper) {
        this.sslHelper = sslHelper;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    public void configure(String baseUrl, Map<String, String> credentials) {
        this.baseUrl = baseUrl;
        this.accessToken = resolveToken(credentials);

        // Azure DevOps PAT uses Basic auth with an empty username: base64(:pat)
        String encoded = Base64.getEncoder().encodeToString(
                (":" + this.accessToken).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        this.webClient = sslHelper.trustedBuilder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + encoded)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("Configured Azure DevOps adapter for {}", baseUrl);
    }

    @Override
    public List<PlatformTaskDto> fetchTasks(String projectKey, PlatformTaskFilter filter) {
        log.info("Fetching work items from Azure DevOps for project {}", projectKey);
        try {
            String[] parts = parseProjectKey(projectKey);
            String org = parts[0];
            String project = parts[1];

            // Build WIQL query
            StringBuilder wiql = new StringBuilder(
                    "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = '")
                    .append(project).append("'");

            if (filter != null && filter.getStatus() != null && !filter.getStatus().isBlank()) {
                wiql.append(" AND [System.State] = '").append(filter.getStatus()).append("'");
            }
            if (filter != null && filter.getAssignee() != null && !filter.getAssignee().isBlank()) {
                wiql.append(" AND [System.AssignedTo] = '").append(filter.getAssignee()).append("'");
            }
            wiql.append(" ORDER BY [System.Id] DESC");

            int maxResults = (filter != null && filter.getMaxResults() != null) ? filter.getMaxResults() : 50;

            Map<String, String> wiqlBody = Map.of("query", wiql.toString());

            String wiqlUri = "/" + org + "/" + project + "/_apis/wit/wiql?api-version=7.0&$top=" + maxResults;

            String wiqlResponse = webClient.post()
                    .uri(wiqlUri)
                    .bodyValue(wiqlBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String htmlError = AdapterErrorHelper.checkForHtmlResponse(wiqlResponse, log);
            if (htmlError != null) {
                throw new RuntimeException("Failed to fetch tasks from Azure DevOps: " + htmlError);
            }

            Map<String, Object> wiqlMap = objectMapper.readValue(wiqlResponse, new TypeReference<>() {});
            List<Map<String, Object>> workItemRefs = castToListOfMaps(wiqlMap.get("workItems"));
            if (workItemRefs == null || workItemRefs.isEmpty()) {
                return Collections.emptyList();
            }

            // Extract IDs and batch-fetch details
            String ids = workItemRefs.stream()
                    .map(wi -> String.valueOf(((Number) wi.get("id")).intValue()))
                    .collect(Collectors.joining(","));

            String detailsUri = "/" + org + "/" + project
                    + "/_apis/wit/workitems?ids=" + ids + "&$expand=all&api-version=7.0";

            String detailsResponse = webClient.get()
                    .uri(detailsUri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Map<String, Object> detailsMap = objectMapper.readValue(detailsResponse, new TypeReference<>() {});
            List<Map<String, Object>> workItems = castToListOfMaps(detailsMap.get("value"));
            if (workItems == null) {
                return Collections.emptyList();
            }

            List<PlatformTaskDto> result = new ArrayList<>();
            for (Map<String, Object> workItem : workItems) {
                result.add(mapWorkItemToPlatformTask(workItem, org, project));
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to fetch work items from Azure DevOps for project {}: {}", projectKey, message, e);
            throw new RuntimeException("Failed to fetch tasks from Azure DevOps: " + message, e);
        }
    }

    @Override
    public PlatformTaskDto getTask(String externalId) {
        log.info("Getting work item {} from Azure DevOps", externalId);
        try {
            String uri = "/_apis/wit/workitems/" + externalId + "?$expand=all&api-version=7.0";

            String responseBody = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String htmlError = AdapterErrorHelper.checkForHtmlResponse(responseBody, log);
            if (htmlError != null) {
                throw new RuntimeException("Failed to get task from Azure DevOps: " + htmlError);
            }

            Map<String, Object> workItem = objectMapper.readValue(responseBody, new TypeReference<>() {});
            return mapWorkItemToPlatformTask(workItem, null, null);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to get work item {} from Azure DevOps: {}", externalId, message, e);
            throw new RuntimeException("Failed to get task from Azure DevOps: " + message, e);
        }
    }

    @Override
    public void updateTaskStatus(String externalId, String status, String comment) {
        log.info("Updating work item {} status to {} on Azure DevOps", externalId, status);
        try {
            String uri = "/_apis/wit/workitems/" + externalId + "?api-version=7.0";

            List<Map<String, String>> patchBody = List.of(
                    Map.of("op", "replace", "path", "/fields/System.State", "value", status)
            );

            String patchJson = objectMapper.writeValueAsString(patchBody);

            webClient.patch()
                    .uri(uri)
                    .header("Content-Type", "application/json-patch+json")
                    .bodyValue(patchJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (comment != null && !comment.isBlank()) {
                addComment(externalId, comment);
            }

            log.info("Successfully updated work item {} status to {}", externalId, status);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to update work item {} status on Azure DevOps: {}", externalId, message, e);
            throw new RuntimeException("Failed to update task status on Azure DevOps: " + message, e);
        }
    }

    @Override
    public void addComment(String externalId, String comment) {
        log.info("Adding comment to work item {} on Azure DevOps", externalId);
        try {
            String uri = "/_apis/wit/workitems/" + externalId + "/comments?api-version=7.0-preview.4";

            Map<String, String> body = Map.of("text", comment);

            webClient.post()
                    .uri(uri)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Successfully added comment to work item {}", externalId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to add comment to work item {} on Azure DevOps: {}", externalId, message, e);
            throw new RuntimeException("Failed to add comment on Azure DevOps: " + message, e);
        }
    }

    @Override
    public List<String> getAvailableStatuses(String projectKey) {
        log.info("Getting available statuses for Azure DevOps project {}", projectKey);
        try {
            String[] parts = parseProjectKey(projectKey);
            String org = parts[0];
            String project = parts[1];

            // Get work item types
            String typesUri = "/" + org + "/" + project + "/_apis/wit/workitemtypes?api-version=7.0";
            String typesResponse = webClient.get()
                    .uri(typesUri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String htmlError = AdapterErrorHelper.checkForHtmlResponse(typesResponse, log);
            if (htmlError != null) {
                throw new RuntimeException("Failed to get available statuses from Azure DevOps: " + htmlError);
            }

            Map<String, Object> typesMap = objectMapper.readValue(typesResponse, new TypeReference<>() {});
            List<Map<String, Object>> types = castToListOfMaps(typesMap.get("value"));

            // Find "Task" type or use the first available type
            String typeName = "Task";
            if (types != null && !types.isEmpty()) {
                boolean hasTask = types.stream()
                        .anyMatch(t -> "Task".equals(t.get("name")));
                if (!hasTask) {
                    typeName = (String) types.get(0).get("name");
                }
            }

            // Get states for the chosen work item type
            String statesUri = "/" + org + "/" + project
                    + "/_apis/wit/workitemtypes/" + typeName + "/states?api-version=7.0";
            String statesResponse = webClient.get()
                    .uri(statesUri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Map<String, Object> statesMap = objectMapper.readValue(statesResponse, new TypeReference<>() {});
            List<Map<String, Object>> states = castToListOfMaps(statesMap.get("value"));

            Set<String> stateNames = new LinkedHashSet<>();
            if (states != null) {
                for (Map<String, Object> state : states) {
                    String name = (String) state.get("name");
                    if (name != null) {
                        stateNames.add(name);
                    }
                }
            }

            return new ArrayList<>(stateNames);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to get available statuses for Azure DevOps project {}: {}", projectKey, message, e);
            throw new RuntimeException("Failed to get available statuses from Azure DevOps: " + message, e);
        }
    }

    @Override
    public boolean testConnection() {
        log.info("Testing Azure DevOps connection to {}", baseUrl);
        if (webClient == null) {
            log.warn("Azure DevOps adapter not configured");
            return false;
        }
        try {
            webClient.get()
                    .uri("/_apis/projects?api-version=7.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("Azure DevOps connection test successful");
            return true;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            log.error("Azure DevOps connection test failed: {}", classified != null ? classified : e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<PlatformProjectDto> getProjects() {
        log.info("Fetching projects from Azure DevOps");
        try {
            String responseBody = webClient.get()
                    .uri("/_apis/projects?api-version=7.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String htmlError = AdapterErrorHelper.checkForHtmlResponse(responseBody, log);
            if (htmlError != null) {
                throw new RuntimeException("Failed to fetch projects from Azure DevOps: " + htmlError);
            }

            Map<String, Object> responseMap = objectMapper.readValue(
                    responseBody, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> projects = castToListOfMaps(responseMap.get("value"));
            if (projects == null) {
                return Collections.emptyList();
            }

            List<PlatformProjectDto> result = new ArrayList<>();
            for (Map<String, Object> project : projects) {
                String name = (String) project.get("name");
                String description = (String) project.get("description");
                String url = baseUrl + "/" + name;

                result.add(PlatformProjectDto.builder()
                        .key(name)
                        .name(name)
                        .description(description)
                        .url(url)
                        .avatarUrl(null)
                        .build());
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to fetch projects from Azure DevOps: {}", message, e);
            throw new RuntimeException("Failed to fetch projects from Azure DevOps: " + message, e);
        }
    }

    // --- Helper methods ---

    /**
     * Parses a projectKey in the format "organization/project" into its component parts.
     */
    String[] parseProjectKey(String projectKey) {
        if (projectKey == null || !projectKey.contains("/")) {
            throw new IllegalArgumentException(
                    "Invalid projectKey format: " + projectKey + ". Expected 'organization/project'");
        }
        int slashIndex = projectKey.indexOf('/');
        String org = projectKey.substring(0, slashIndex);
        String project = projectKey.substring(slashIndex + 1);
        if (org.isBlank() || project.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid projectKey format: " + projectKey + ". Expected 'organization/project'");
        }
        return new String[]{org, project};
    }

    @SuppressWarnings("unchecked")
    private PlatformTaskDto mapWorkItemToPlatformTask(Map<String, Object> workItem,
                                                       String org, String project) {
        Number idNum = (Number) workItem.get("id");
        String id = idNum != null ? String.valueOf(idNum.intValue()) : null;

        Map<String, Object> fields = castToMap(workItem.get("fields"));

        String title = fields != null ? (String) fields.get("System.Title") : null;
        String description = fields != null ? (String) fields.get("System.Description") : null;
        String state = fields != null ? (String) fields.get("System.State") : null;
        String priority = mapPriority(fields);
        String assignee = extractAssignee(fields);
        List<String> labels = extractTags(fields);
        Instant createdAt = parseInstant(fields != null ? (String) fields.get("System.CreatedDate") : null);
        Instant updatedAt = parseInstant(fields != null ? (String) fields.get("System.ChangedDate") : null);

        // Build external URL
        String externalUrl;
        if (org != null && project != null) {
            externalUrl = baseUrl + "/" + org + "/" + project + "/_workitems/edit/" + id;
        } else {
            externalUrl = baseUrl + "/_workitems/edit/" + id;
        }

        return PlatformTaskDto.builder()
                .externalId(id)
                .externalUrl(externalUrl)
                .title(title)
                .description(description)
                .status(state)
                .priority(priority)
                .assignee(assignee)
                .labels(labels)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    private String mapPriority(Map<String, Object> fields) {
        if (fields == null) return null;
        Object priorityObj = fields.get("Microsoft.VSTS.Common.Priority");
        if (priorityObj == null) return null;
        int priorityVal;
        if (priorityObj instanceof Number) {
            priorityVal = ((Number) priorityObj).intValue();
        } else {
            try {
                priorityVal = Integer.parseInt(priorityObj.toString());
            } catch (NumberFormatException e) {
                return priorityObj.toString();
            }
        }
        switch (priorityVal) {
            case 1: return "Critical";
            case 2: return "High";
            case 3: return "Medium";
            case 4: return "Low";
            default: return String.valueOf(priorityVal);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractAssignee(Map<String, Object> fields) {
        if (fields == null) return null;
        Object assignedTo = fields.get("System.AssignedTo");
        if (assignedTo == null) return null;
        if (assignedTo instanceof String) {
            return (String) assignedTo;
        }
        if (assignedTo instanceof Map) {
            Map<String, Object> assigneeMap = (Map<String, Object>) assignedTo;
            String displayName = (String) assigneeMap.get("displayName");
            return displayName;
        }
        return assignedTo.toString();
    }

    private List<String> extractTags(Map<String, Object> fields) {
        if (fields == null) return Collections.emptyList();
        Object tagsObj = fields.get("System.Tags");
        if (tagsObj == null) return Collections.emptyList();
        String tagsStr = tagsObj.toString();
        if (tagsStr.isBlank()) return Collections.emptyList();

        List<String> tags = new ArrayList<>();
        for (String tag : tagsStr.split(";")) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return tags;
    }

    private Instant parseInstant(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return Instant.parse(dateStr);
        } catch (DateTimeParseException e) {
            log.warn("Unable to parse date: {}", dateStr);
            return null;
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
