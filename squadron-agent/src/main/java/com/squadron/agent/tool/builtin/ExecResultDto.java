package com.squadron.agent.tool.builtin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the result of executing a command inside a workspace container.
 * Maps to the response from {@code POST /api/workspaces/{id}/exec}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecResultDto {

    /** Process exit code (0 = success) */
    private int exitCode;

    /** Standard output from the command */
    private String stdout;

    /** Standard error from the command */
    private String stderr;
}
