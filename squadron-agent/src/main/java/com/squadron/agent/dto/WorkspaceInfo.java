package com.squadron.agent.dto;

import com.squadron.common.security.GitAuthMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Information about a provisioned workspace, including the workspace ID,
 * branch name, repository URL, and authentication mode. Passed between
 * services during the agent workflow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceInfo {

    /** The workspace ID (used to address the container) */
    private UUID workspaceId;

    /** The branch created for this task */
    private String branchName;

    /** The git repository URL */
    private String repoUrl;

    /** The authentication mode used for git operations */
    private GitAuthMode gitAuthMode;

    /** The container ID (if different from workspaceId) */
    private String containerId;
}
