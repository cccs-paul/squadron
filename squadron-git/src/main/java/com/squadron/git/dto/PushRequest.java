package com.squadron.git.dto;

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
public class PushRequest {

    @NotNull
    private UUID workspaceId;

    @Builder.Default
    private String remoteName = "origin";

    private String branch;

    private String accessToken;
}
