package com.squadron.platform.adapter.github;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Adapter for GitHub Issues using the GitHub REST API.
 * API base: https://api.github.com
 *
 * The projectKey is expected in the format "owner/repo".
 */
@Component
public class GitHubIssuesAdapter implements TicketingPlatformAdapter {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssuesAdapter.class);
    private static final String PLATFORM_TYPE = "GITHUB";
    private static final String DEFAULT_BASE_URL = "https://api.github.com";

    private final WebClientSslHelper sslHelper;
    private final ObjectMapper objectMapper;
    private WebClient webClient;
    private String baseUrl;
    private String accessToken;

    public GitHubIssuesAdapter(WebClientSslHelper sslHelper) {
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
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : DEFAULT_BASE_URL;
        this.accessToken = resolveToken(credentials);
        this.webClient = sslHelper.trustedBuilder()
                .baseUrl(this.baseUrl)
                .defaultHeader("Authorization", "Bearer " + this.accessToken)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
        log.info("Configured GitHub Issues adapter for {}", this.baseUrl);
    }

    @Override
    public List<PlatformTaskDto> fetchTasks(String projectKey, PlatformTaskFilter filter) {
        log.info("Fetching issues from GitHub for repo {}", projectKey);
        try {
            String state = "all";
            String assignee = null;
            int perPage = 50;

            if (filter != null) {
                if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                    state = filter.getStatus();
                }
                if (filter.getAssignee() != null && !filter.getAssignee().isBlank()) {
                    assignee = filter.getAssignee();
                }
                if (filter.getMaxResults() != null) {
                    perPage = filter.getMaxResults();
                }
            }

            StringBuilder uri = new StringBuilder("/repos/" + projectKey + "/issues?state=" + state + "&per_page=" + perPage);
            if (assignee != null) {
                uri.append("&assignee=").append(assignee);
            }

            String responseBody = webClient.get()
                    .uri(uri.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String htmlError = AdapterErrorHelper.checkForHtmlResponse(responseBody, log);
            if (htmlError != null) {
                throw new RuntimeException("Failed to fetch tasks from GitHub: " + htmlError);
            }

            List<Map<String, Object>> issues = objectMapper.readValue(
                    responseBody, new TypeReference<List<Map<String, Object>>>() {});

            List<PlatformTaskDto> tasks = new ArrayList<>();
            for (Map<String, Object> issue : issues) {
                // Filter out pull requests — GitHub API returns PRs mixed with issues
                if (issue.containsKey("pull_request")) {
                    continue;
                }
                tasks.add(mapIssueToPlatformTask(projectKey, issue));
            }
            return tasks;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to fetch tasks from GitHub for repo {}: {}", projectKey, message, e);
            throw new RuntimeException("Failed to fetch tasks from GitHub: " + message, e);
        }
    }

    @Override
    public PlatformTaskDto getTask(String externalId) {
        log.info("Getting issue {} from GitHub", externalId);
        try {
            String[] parts = parseExternalId(externalId);
            String owner = parts[0];
            String repo = parts[1];
            String number = parts[2];

            String responseBody = webClient.get()
                    .uri("/repos/" + owner + "/" + repo + "/issues/" + number)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String htmlError = AdapterErrorHelper.checkForHtmlResponse(responseBody, log);
            if (htmlError != null) {
                throw new RuntimeException("Failed to get task from GitHub: " + htmlError);
            }

            Map<String, Object> issue = objectMapper.readValue(
                    responseBody, new TypeReference<Map<String, Object>>() {});
            return mapIssueToPlatformTask(owner + "/" + repo, issue);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to get task {} from GitHub: {}", externalId, message, e);
            throw new RuntimeException("Failed to get task from GitHub: " + message, e);
        }
    }

    @Override
    public void updateTaskStatus(String externalId, String status, String comment) {
        log.info("Updating issue {} status to {} on GitHub", externalId, status);
        try {
            String[] parts = parseExternalId(externalId);
            String owner = parts[0];
            String repo = parts[1];
            String number = parts[2];

            Map<String, String> body = Map.of("state", status);

            webClient.patch()
                    .uri("/repos/" + owner + "/" + repo + "/issues/" + number)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (comment != null && !comment.isBlank()) {
                addComment(externalId, comment);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to update task {} status on GitHub: {}", externalId, message, e);
            throw new RuntimeException("Failed to update task status on GitHub: " + message, e);
        }
    }

    @Override
    public void addComment(String externalId, String comment) {
        log.info("Adding comment to issue {} on GitHub", externalId);
        try {
            String[] parts = parseExternalId(externalId);
            String owner = parts[0];
            String repo = parts[1];
            String number = parts[2];

            Map<String, String> body = Map.of("body", comment);

            webClient.post()
                    .uri("/repos/" + owner + "/" + repo + "/issues/" + number + "/comments")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to add comment to task {} on GitHub: {}", externalId, message, e);
            throw new RuntimeException("Failed to add comment on GitHub: " + message, e);
        }
    }

    @Override
    public List<String> getAvailableStatuses(String projectKey) {
        log.info("Getting available statuses for GitHub repo {}", projectKey);
        // GitHub issues only have "open" and "closed" states
        return List.of("open", "closed");
    }

    @Override
    public boolean testConnection() {
        log.info("Testing GitHub connection to {}", baseUrl);
        if (webClient == null) {
            log.warn("GitHub adapter not configured");
            return false;
        }
        try {
            webClient.get()
                    .uri("/user")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("GitHub connection test successful");
            return true;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("GitHub connection test failed: {}", message, e);
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PlatformProjectDto> getProjects() {
        log.info("Fetching repositories from GitHub");
        try {
            String responseBody = webClient.get()
                    .uri("/user/repos?per_page=100&sort=updated")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String htmlError = AdapterErrorHelper.checkForHtmlResponse(responseBody, log);
            if (htmlError != null) {
                throw new RuntimeException("Failed to fetch projects from GitHub: " + htmlError);
            }

            List<Map<String, Object>> repos = objectMapper.readValue(
                    responseBody, new TypeReference<List<Map<String, Object>>>() {});

            List<PlatformProjectDto> result = new ArrayList<>();
            for (Map<String, Object> repo : repos) {
                String fullName = (String) repo.get("full_name");
                String name = (String) repo.get("name");
                String description = (String) repo.get("description");
                String htmlUrl = (String) repo.get("html_url");

                String avatarUrl = null;
                Object ownerObj = repo.get("owner");
                if (ownerObj instanceof Map) {
                    avatarUrl = (String) ((Map<String, Object>) ownerObj).get("avatar_url");
                }

                result.add(PlatformProjectDto.builder()
                        .key(fullName)
                        .name(name)
                        .description(description)
                        .url(htmlUrl)
                        .avatarUrl(avatarUrl)
                        .build());
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = AdapterErrorHelper.classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to fetch projects from GitHub: {}", message, e);
            throw new RuntimeException("Failed to fetch projects from GitHub: " + message, e);
        }
    }

    /**
     * Parses an externalId in the format "owner/repo#number" into its component parts.
     *
     * @param externalId the external ID string
     * @return a String array of [owner, repo, number]
     */
    String[] parseExternalId(String externalId) {
        int hashIndex = externalId.lastIndexOf('#');
        if (hashIndex < 0) {
            throw new IllegalArgumentException("Invalid externalId format: " + externalId + ". Expected 'owner/repo#number'");
        }
        String ownerRepo = externalId.substring(0, hashIndex);
        String number = externalId.substring(hashIndex + 1);

        int slashIndex = ownerRepo.indexOf('/');
        if (slashIndex < 0) {
            throw new IllegalArgumentException("Invalid externalId format: " + externalId + ". Expected 'owner/repo#number'");
        }
        String owner = ownerRepo.substring(0, slashIndex);
        String repo = ownerRepo.substring(slashIndex + 1);

        return new String[]{owner, repo, number};
    }

    @SuppressWarnings("unchecked")
    private PlatformTaskDto mapIssueToPlatformTask(String projectKey, Map<String, Object> issue) {
        int number = (Integer) issue.get("number");
        String htmlUrl = (String) issue.get("html_url");
        String title = (String) issue.get("title");
        String body = (String) issue.get("body");
        String state = (String) issue.get("state");

        String assigneeLogin = null;
        Object assigneeObj = issue.get("assignee");
        if (assigneeObj instanceof Map) {
            assigneeLogin = (String) ((Map<String, Object>) assigneeObj).get("login");
        }

        List<String> labelNames = new ArrayList<>();
        Object labelsObj = issue.get("labels");
        if (labelsObj instanceof List) {
            for (Object labelObj : (List<Object>) labelsObj) {
                if (labelObj instanceof Map) {
                    String name = (String) ((Map<String, Object>) labelObj).get("name");
                    if (name != null) {
                        labelNames.add(name);
                    }
                }
            }
        }

        Instant createdAt = null;
        Object createdAtObj = issue.get("created_at");
        if (createdAtObj instanceof String) {
            createdAt = Instant.parse((String) createdAtObj);
        }

        Instant updatedAt = null;
        Object updatedAtObj = issue.get("updated_at");
        if (updatedAtObj instanceof String) {
            updatedAt = Instant.parse((String) updatedAtObj);
        }

        return PlatformTaskDto.builder()
                .externalId(projectKey + "#" + number)
                .externalUrl(htmlUrl)
                .title(title)
                .description(body)
                .status(state)
                .priority(null)
                .assignee(assigneeLogin)
                .labels(labelNames)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
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
