package com.squadron.git.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeabilityDto {
    private boolean mergeable;
    @Builder.Default
    private List<String> conflictFiles = List.of();
}
