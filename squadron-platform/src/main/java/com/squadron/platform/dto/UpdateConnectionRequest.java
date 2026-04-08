package com.squadron.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for updating an existing platform connection.
 * All fields are optional — only provided fields are applied.
 * The tenantId is never changed on update (preserved from the existing entity).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConnectionRequest {

    private String name;

    private String platformType;

    private String baseUrl;

    private String authType;

    private Map<String, String> credentials;

    private Map<String, Object> metadata;
}
