package com.squadron.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreferenceRequest {

    private Boolean enableEmail;
    private Boolean enableSlack;
    private Boolean enableTeams;
    private Boolean enableInApp;
    private String slackWebhookUrl;
    private String teamsWebhookUrl;
    private String emailAddress;
    private List<String> mutedEventTypes;
}
