package com.squadron.notification.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.notification.dto.NotificationPreferenceDto;
import com.squadron.notification.dto.UpdatePreferenceRequest;
import com.squadron.notification.service.NotificationPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications/preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    @GetMapping("/user/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationPreferenceDto>> getPreferences(
            @PathVariable UUID userId) {
        NotificationPreferenceDto preference = preferenceService.getPreference(userId);
        return ResponseEntity.ok(ApiResponse.success(preference));
    }

    @PutMapping("/user/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationPreferenceDto>> updatePreferences(
            @PathVariable UUID userId,
            @RequestHeader(value = "X-Tenant-Id") UUID tenantId,
            @RequestBody UpdatePreferenceRequest request) {
        NotificationPreferenceDto preference = preferenceService.createOrUpdatePreference(
                userId, tenantId, request);
        return ResponseEntity.ok(ApiResponse.success(preference));
    }
}
