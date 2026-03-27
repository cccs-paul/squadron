package com.squadron.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationRequest {

    @NotNull
    private UUID tenantId;

    private UUID userId; // may be null for tenant-level notifications

    @NotBlank
    private String channel;

    @NotBlank
    private String subject;

    @NotBlank
    private String body;

    private UUID relatedTaskId;

    private String eventType;
}
