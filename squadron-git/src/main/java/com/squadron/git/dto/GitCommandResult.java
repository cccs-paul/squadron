package com.squadron.git.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitCommandResult {

    private boolean success;
    private String output;
    private String errorOutput;
    private int exitCode;
}
