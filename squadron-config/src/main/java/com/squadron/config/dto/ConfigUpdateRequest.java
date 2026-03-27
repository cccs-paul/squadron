package com.squadron.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigUpdateRequest {

    @NotBlank(message = "Config key must not be blank")
    private String configKey;

    @NotBlank(message = "Config value must not be blank")
    private String configValue;

    private String description;
}
