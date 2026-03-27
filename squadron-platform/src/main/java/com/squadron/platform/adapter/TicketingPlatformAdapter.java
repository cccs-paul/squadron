package com.squadron.platform.adapter;

import com.squadron.platform.dto.PlatformTaskDto;
import com.squadron.platform.dto.PlatformTaskFilter;

import java.util.List;

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
     */
    void configure(String baseUrl, String accessToken);

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
}
