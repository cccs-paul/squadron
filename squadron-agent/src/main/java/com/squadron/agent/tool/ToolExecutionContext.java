package com.squadron.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Context passed to an {@link AgentTool} when it is executed.
 * Contains workspace/task identifiers, parameters, and optional credentials.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionContext {

    /** Which workspace container to execute in */
    private UUID workspaceId;

    /** The task this tool execution is for */
    private UUID taskId;

    /** Tenant identifier for multi-tenancy */
    private UUID tenantId;

    /** The actual parameter values passed by the agent */
    private Map<String, Object> parameters;

    /** Optional OAuth token for git operations */
    private String accessToken;
}
