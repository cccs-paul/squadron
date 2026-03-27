package com.squadron.orchestrator.dto;

import lombok.*;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TaskStatsDto {
    private long total;
    private Map<String, Long> byState;
    private Map<String, Long> byPriority;
}
