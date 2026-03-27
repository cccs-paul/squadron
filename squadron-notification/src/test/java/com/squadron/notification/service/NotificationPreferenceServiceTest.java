package com.squadron.notification.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.notification.dto.NotificationPreferenceDto;
import com.squadron.notification.dto.UpdatePreferenceRequest;
import com.squadron.notification.entity.NotificationPreference;
import com.squadron.notification.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    private NotificationPreferenceService preferenceService;

    @BeforeEach
    void setUp() {
        preferenceService = new NotificationPreferenceService(preferenceRepository);
    }

    @Test
    void should_getPreference_when_exists() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        NotificationPreference preference = NotificationPreference.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tenantId(tenantId)
                .enableEmail(true)
                .enableSlack(false)
                .enableTeams(false)
                .enableInApp(true)
                .emailAddress("user@example.com")
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));

        NotificationPreferenceDto result = preferenceService.getPreference(userId);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertTrue(result.getEnableEmail());
        assertFalse(result.getEnableSlack());
        assertEquals("user@example.com", result.getEmailAddress());
    }

    @Test
    void should_throwNotFound_when_preferenceDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> preferenceService.getPreference(userId));
    }

    @Test
    void should_createPreference_when_doesNotExist() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        UpdatePreferenceRequest request = UpdatePreferenceRequest.builder()
                .enableEmail(true)
                .enableSlack(true)
                .slackWebhookUrl("https://hooks.slack.com/test")
                .emailAddress("user@example.com")
                .mutedEventTypes(List.of("TASK_STATE_CHANGED"))
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> {
                    NotificationPreference p = invocation.getArgument(0);
                    p.setId(UUID.randomUUID());
                    p.setCreatedAt(Instant.now());
                    p.setUpdatedAt(Instant.now());
                    return p;
                });

        NotificationPreferenceDto result = preferenceService.createOrUpdatePreference(
                userId, tenantId, request);

        assertNotNull(result);
        assertTrue(result.getEnableEmail());
        assertTrue(result.getEnableSlack());
        assertEquals("https://hooks.slack.com/test", result.getSlackWebhookUrl());
        assertEquals("user@example.com", result.getEmailAddress());
        assertEquals(1, result.getMutedEventTypes().size());

        verify(preferenceRepository).save(any(NotificationPreference.class));
    }

    @Test
    void should_updatePreference_when_alreadyExists() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        NotificationPreference existing = NotificationPreference.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tenantId(tenantId)
                .enableEmail(true)
                .enableSlack(false)
                .enableTeams(false)
                .enableInApp(true)
                .emailAddress("old@example.com")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        UpdatePreferenceRequest request = UpdatePreferenceRequest.builder()
                .enableSlack(true)
                .slackWebhookUrl("https://hooks.slack.com/new")
                .emailAddress("new@example.com")
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(preferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreferenceDto result = preferenceService.createOrUpdatePreference(
                userId, tenantId, request);

        assertNotNull(result);
        assertTrue(result.getEnableSlack());
        assertEquals("https://hooks.slack.com/new", result.getSlackWebhookUrl());
        assertEquals("new@example.com", result.getEmailAddress());
        // Unchanged fields should remain
        assertTrue(result.getEnableEmail());
    }

    @Test
    void should_onlyUpdateProvidedFields_when_otherFieldsNull() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        NotificationPreference existing = NotificationPreference.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tenantId(tenantId)
                .enableEmail(true)
                .enableSlack(false)
                .enableTeams(false)
                .enableInApp(true)
                .emailAddress("user@example.com")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Only update enableSlack
        UpdatePreferenceRequest request = UpdatePreferenceRequest.builder()
                .enableSlack(true)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(preferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreferenceDto result = preferenceService.createOrUpdatePreference(
                userId, tenantId, request);

        assertTrue(result.getEnableSlack());
        assertTrue(result.getEnableEmail()); // unchanged
        assertEquals("user@example.com", result.getEmailAddress()); // unchanged
    }

    @Test
    void should_handleMutedEventTypes_when_preferencesHasJson() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        NotificationPreference preference = NotificationPreference.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tenantId(tenantId)
                .enableEmail(true)
                .enableSlack(false)
                .enableTeams(false)
                .enableInApp(true)
                .mutedEventTypes("[\"TASK_STATE_CHANGED\",\"AGENT_COMPLETED\"]")
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));

        NotificationPreferenceDto result = preferenceService.getPreference(userId);

        assertNotNull(result.getMutedEventTypes());
        assertEquals(2, result.getMutedEventTypes().size());
        assertEquals("TASK_STATE_CHANGED", result.getMutedEventTypes().get(0));
        assertEquals("AGENT_COMPLETED", result.getMutedEventTypes().get(1));
    }

    @Test
    void should_handleNullMutedEventTypes_when_preferencesHasNone() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        NotificationPreference preference = NotificationPreference.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tenantId(tenantId)
                .enableEmail(true)
                .enableSlack(false)
                .enableTeams(false)
                .enableInApp(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));

        NotificationPreferenceDto result = preferenceService.getPreference(userId);

        assertNotNull(result.getMutedEventTypes());
        assertTrue(result.getMutedEventTypes().isEmpty());
    }
}
