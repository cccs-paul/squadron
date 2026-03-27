package com.squadron.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformTaskFilter {

    private String projectKey;
    private String status;
    private String assignee;

    @Builder.Default
    private Integer maxResults = 50;
}
