package com.squadron.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;        // seconds until access token expiry
    private UserDto user;
}
