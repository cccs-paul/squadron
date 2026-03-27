package com.squadron.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceDto {

    private UUID id;
    private UUID userId;
    private UUID tenantId;
    private Boolean enableEmail;
    private Boolean enableSlack;
    private Boolean enableTeams;
    private Boolean enableInApp;
    private String slackWebhookUrl;
    private String teamsWebhookUrl;
    private String emailAddress;
    private List<String> mutedEventTypes;
    private Instant createdAt;
    private Instant updatedAt;
}
