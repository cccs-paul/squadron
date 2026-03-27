package com.squadron.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.squadron.notification.dto.NotificationPreferenceDto;
import com.squadron.notification.dto.UpdatePreferenceRequest;
import com.squadron.notification.entity.NotificationPreference;
import com.squadron.notification.repository.NotificationPreferenceRepository;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    @Transactional(readOnly = true)
    public NotificationPreferenceDto getPreference(UUID userId) {
        NotificationPreference preference = preferenceRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "NotificationPreference", userId));
        return toDto(preference);
    }

    public NotificationPreferenceDto createOrUpdatePreference(UUID userId, UUID tenantId,
                                                               UpdatePreferenceRequest request) {
        log.info("Updating notification preferences for user {}", userId);

        NotificationPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> NotificationPreference.builder()
                        .userId(userId)
                        .tenantId(tenantId)
                        .build());

        if (request.getEnableEmail() != null) {
            preference.setEnableEmail(request.getEnableEmail());
        }
        if (request.getEnableSlack() != null) {
            preference.setEnableSlack(request.getEnableSlack());
        }
        if (request.getEnableTeams() != null) {
            preference.setEnableTeams(request.getEnableTeams());
        }
        if (request.getEnableInApp() != null) {
            preference.setEnableInApp(request.getEnableInApp());
        }
        if (request.getSlackWebhookUrl() != null) {
            preference.setSlackWebhookUrl(request.getSlackWebhookUrl());
        }
        if (request.getTeamsWebhookUrl() != null) {
            preference.setTeamsWebhookUrl(request.getTeamsWebhookUrl());
        }
        if (request.getEmailAddress() != null) {
            preference.setEmailAddress(request.getEmailAddress());
        }
        if (request.getMutedEventTypes() != null) {
            preference.setMutedEventTypes(JsonUtils.toJson(request.getMutedEventTypes()));
        }

        preference = preferenceRepository.save(preference);
        return toDto(preference);
    }

    private NotificationPreferenceDto toDto(NotificationPreference preference) {
        List<String> mutedEventTypes = Collections.emptyList();
        if (preference.getMutedEventTypes() != null && !preference.getMutedEventTypes().isBlank()) {
            mutedEventTypes = JsonUtils.fromJson(
                    preference.getMutedEventTypes(),
                    new TypeReference<List<String>>() {}
            );
        }

        return NotificationPreferenceDto.builder()
                .id(preference.getId())
                .userId(preference.getUserId())
                .tenantId(preference.getTenantId())
                .enableEmail(preference.getEnableEmail())
                .enableSlack(preference.getEnableSlack())
                .enableTeams(preference.getEnableTeams())
                .enableInApp(preference.getEnableInApp())
                .slackWebhookUrl(preference.getSlackWebhookUrl())
                .teamsWebhookUrl(preference.getTeamsWebhookUrl())
                .emailAddress(preference.getEmailAddress())
                .mutedEventTypes(mutedEventTypes)
                .createdAt(preference.getCreatedAt())
                .updatedAt(preference.getUpdatedAt())
                .build();
    }
}
