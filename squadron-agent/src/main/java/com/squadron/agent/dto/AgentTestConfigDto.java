package com.squadron.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for reading/writing agent test generator configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTestConfigDto {

    @NotBlank(message = "Generator provider is required")
    @Size(max = 100)
    @Builder.Default
    private String generatorProvider = "ollama";

    @NotBlank(message = "Generator model is required")
    @Size(max = 200)
    @Builder.Default
    private String generatorModel = "gemma4:e2b";

    @Builder.Default
    private String generatorHostingType = "SELF_HOSTED";

    @Size(max = 500)
    private String generatorBaseUrl;

    private String generatorApiKey;
}
