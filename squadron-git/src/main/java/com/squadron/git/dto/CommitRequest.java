package com.squadron.git.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitRequest {

    @NotNull
    private UUID workspaceId;

    @NotBlank
    private String message;

    private String authorName;

    private String authorEmail;
}
