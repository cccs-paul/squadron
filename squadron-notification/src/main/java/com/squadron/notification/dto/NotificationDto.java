package com.squadron.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private UUID id;
    private UUID tenantId;
    private UUID userId;
    private String channel;
    private String subject;
    private String body;
    private String status;
    private UUID relatedTaskId;
    private String eventType;
    private String errorMessage;
    private Instant createdAt;
    private Instant sentAt;
    private Instant readAt;
}
