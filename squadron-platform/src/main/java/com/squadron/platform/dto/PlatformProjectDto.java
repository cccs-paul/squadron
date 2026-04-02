package com.squadron.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a project/repository from a remote ticketing or Git platform.
 * Used to populate the project import UI, where users select which remote
 * projects to track in Squadron.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformProjectDto {

    /** Platform-specific project identifier (e.g., JIRA project key, GitHub "owner/repo", GitLab project ID). */
    private String key;

    /** Human-readable project name. */
    private String name;

    /** Optional description of the project. */
    private String description;

    /** URL to the project on the external platform. */
    private String url;

    /** Avatar/icon URL for the project, if available. */
    private String avatarUrl;
}
