package com.squadron.platform.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.platform.dto.ConnectionInfoResponse;
import com.squadron.platform.dto.LinkAccountRequest;
import com.squadron.platform.dto.OAuth2AuthorizeUrlResponse;
import com.squadron.platform.dto.OAuth2CallbackRequest;
import com.squadron.platform.dto.OAuth2LinkRequest;
import com.squadron.platform.dto.PatLinkRequest;
import com.squadron.platform.dto.UserPlatformTokenResponse;
import com.squadron.platform.entity.UserPlatformToken;
import com.squadron.platform.service.UserTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/platforms/tokens")
public class UserTokenController {

    private final UserTokenService tokenService;

    public UserTokenController(UserTokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * Link a user's platform account via OAuth2 authorization code exchange.
     * Exchanges the code for tokens, encrypts them, and stores them.
     */
    @PostMapping("/oauth2/link")
    public ResponseEntity<ApiResponse<UserPlatformTokenResponse>> linkOAuth2Account(
            @Valid @RequestBody OAuth2LinkRequest request) {
        UserPlatformToken token = tokenService.linkOAuth2Account(
                request.getUserId(),
                request.getConnectionId(),
                request.getAuthorizationCode(),
                request.getRedirectUri());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(UserPlatformTokenResponse.fromEntity(token)));
    }

    /**
     * Link a user's platform account via personal access token (PAT).
     * Encrypts the PAT and stores it.
     */
    @PostMapping("/pat/link")
    public ResponseEntity<ApiResponse<UserPlatformTokenResponse>> linkPatAccount(
            @Valid @RequestBody PatLinkRequest request) {
        UserPlatformToken token = tokenService.linkPatAccount(
                request.getUserId(),
                request.getConnectionId(),
                request.getAccessToken());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(UserPlatformTokenResponse.fromEntity(token)));
    }

    /**
     * Legacy link endpoint. Uses OAuth2 code exchange flow.
     * Returns a safe DTO that does not expose raw/encrypted tokens.
     */
    @PostMapping("/link")
    public ResponseEntity<ApiResponse<UserPlatformTokenResponse>> linkAccount(
            @RequestParam UUID userId,
            @Valid @RequestBody LinkAccountRequest request) {
        UserPlatformToken token = tokenService.linkAccount(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(UserPlatformTokenResponse.fromEntity(token)));
    }

    /**
     * List all platform token links for a user.
     * Returns safe DTOs that do not expose raw/encrypted tokens.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<UserPlatformTokenResponse>>> listByUser(
            @PathVariable UUID userId) {
        List<UserPlatformToken> tokens = tokenService.getTokensByUser(userId);
        List<UserPlatformTokenResponse> responses = tokens.stream()
                .map(UserPlatformTokenResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @DeleteMapping("/user/{userId}/connection/{connectionId}")
    public ResponseEntity<Void> unlinkAccount(
            @PathVariable UUID userId,
            @PathVariable UUID connectionId) {
        tokenService.unlinkAccount(userId, connectionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * List available platform connections for a tenant.
     * Returns safe DTOs that do not expose credentials.
     */
    @GetMapping("/connections/{tenantId}")
    public ResponseEntity<ApiResponse<List<ConnectionInfoResponse>>> getAvailableConnections(
            @PathVariable UUID tenantId) {
        List<ConnectionInfoResponse> connections = tokenService.getAvailableConnections(tenantId);
        return ResponseEntity.ok(ApiResponse.success(connections));
    }

    /**
     * Generate an OAuth2 authorization URL for the given platform connection.
     * Returns the URL the frontend should redirect the user to, along with the state parameter.
     */
    @GetMapping("/oauth2/authorize/{connectionId}")
    public ResponseEntity<ApiResponse<OAuth2AuthorizeUrlResponse>> generateOAuth2AuthorizeUrl(
            @PathVariable UUID connectionId) {
        OAuth2AuthorizeUrlResponse response = tokenService.generateOAuth2AuthorizeUrl(connectionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Handle OAuth2 callback with state validation.
     * Validates the CSRF state parameter and exchanges the authorization code for tokens.
     */
    @PostMapping("/oauth2/callback")
    public ResponseEntity<ApiResponse<UserPlatformTokenResponse>> handleOAuth2Callback(
            @Valid @RequestBody OAuth2CallbackRequest request) {
        UserPlatformToken token = tokenService.linkOAuth2AccountWithState(
                request.getUserId(),
                request.getConnectionId(),
                request.getAuthorizationCode(),
                request.getRedirectUri(),
                request.getState());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(UserPlatformTokenResponse.fromEntity(token)));
    }
}
