package com.squadron.notification.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.notification.dto.NotificationDto;
import com.squadron.notification.dto.SendNotificationRequest;
import com.squadron.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationDto>> sendNotification(
            @Valid @RequestBody SendNotificationRequest request) {
        NotificationDto notification = notificationService.sendNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(notification));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> listNotifications(
            @PathVariable UUID userId) {
        List<NotificationDto> notifications = notificationService.listNotifications(userId);
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    @GetMapping("/user/{userId}/unread")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> listUnread(
            @PathVariable UUID userId) {
        List<NotificationDto> notifications = notificationService.listUnread(userId);
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    @GetMapping("/user/{userId}/unread/count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Long>> countUnread(
            @PathVariable UUID userId) {
        long count = notificationService.countUnread(userId);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationDto>> markAsRead(
            @PathVariable UUID id) {
        NotificationDto notification = notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.success(notification));
    }

    @PutMapping("/user/{userId}/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @PathVariable UUID userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
