package com.squadron.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestGitAccessRequest {

    @NotBlank
    private String cloneUrl;

    private String accessToken;

    private UUID sshKeyId;

    private String branch;
}
