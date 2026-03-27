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
public class MergeRequest {

    @NotNull
    private UUID pullRequestRecordId;

    @Builder.Default
    private String mergeStrategy = "MERGE";

    private String accessToken;
}
