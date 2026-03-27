package com.squadron.orchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSyncResult {

    private int created;
    private int updated;
    private int unchanged;
    private int failed;
    private List<String> errors;
}
