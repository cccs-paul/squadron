package com.squadron.platform.adapter;

import com.squadron.platform.dto.PlatformProjectDto;
import com.squadron.platform.dto.PlatformTaskDto;
import com.squadron.platform.dto.PlatformTaskFilter;

import java.util.List;
import java.util.Map;

/**
 * Common interface for all ticketing platform adapters.
 * Each implementation handles communication with a specific platform API.
 */
public interface TicketingPlatformAdapter {

    /**
     * Returns the platform type identifier (e.g., JIRA_CLOUD, GITHUB, GITLAB, AZURE_DEVOPS).
     */
    String getPlatformType();

    /**
     * Configures the adapter with connection details.
     *
     * @param baseUrl     the base URL of the external platform instance
     * @param credentials the decrypted credentials map (keys vary by platform and auth type,
     *                    e.g. {@code email}+{@code apiToken} for Jira Cloud API Token auth,
     *                    {@code pat} for PAT-based auth, {@code username}+{@code password}
     *                    for Basic auth, etc.)
     */
    void configure(String baseUrl, Map<String, String> credentials);

    /**
     * Fetches tasks from the platform matching the given filter.
     */
    List<PlatformTaskDto> fetchTasks(String projectKey, PlatformTaskFilter filter);

    /**
     * Retrieves a single task by its external ID.
     */
    PlatformTaskDto getTask(String externalId);

    /**
     * Updates the status of a task on the platform.
     */
    void updateTaskStatus(String externalId, String status, String comment);

    /**
     * Adds a comment to a task on the platform.
     */
    void addComment(String externalId, String comment);

    /**
     * Returns the list of available statuses for a project.
     */
    List<String> getAvailableStatuses(String projectKey);

    /**
     * Tests whether the connection to the platform is working.
     */
    boolean testConnection();

    /**
     * Returns the list of projects/repositories available on the connected platform.
     * Used to populate the project import UI where users select which remote projects
     * to track in Squadron.
     */
    List<PlatformProjectDto> getProjects();
}
