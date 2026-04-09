package com.squadron.workspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestGitAccessResult {

    private boolean success;
    private String message;
    private String branch;
    private long durationMs;
}
