package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfigDto {

    private String provider;
    private String model;
    private Integer maxTokens;
    private Double temperature;
    private String systemPromptOverride;
}
