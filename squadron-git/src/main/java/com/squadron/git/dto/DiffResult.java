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
public class DiffResult {

    private List<DiffFile> files;
    private int totalAdditions;
    private int totalDeletions;
}
