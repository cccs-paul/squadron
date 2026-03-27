package com.squadron.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2AuthorizeUrlResponse {

    private String authorizeUrl;
    private String state;
    private UUID connectionId;
    private String platformType;
}
