package com.squadron.workspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecResult {

    private int exitCode;
    private String stdout;
    private String stderr;
    private long durationMs;
}
