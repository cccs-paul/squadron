package com.squadron.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squadron.notification.dto.NotificationPreferenceDto;
import com.squadron.notification.dto.UpdatePreferenceRequest;
import com.squadron.notification.service.NotificationPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceControllerTest {

    @Mock
    private NotificationPreferenceService preferenceService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        NotificationPreferenceController controller = new NotificationPreferenceController(preferenceService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void should_getPreferences_when_called() throws Exception {
        UUID userId = UUID.randomUUID();

        NotificationPreferenceDto dto = NotificationPreferenceDto.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tenantId(UUID.randomUUID())
                .enableEmail(true)
                .enableSlack(false)
                .enableTeams(false)
                .enableInApp(true)
                .emailAddress("user@example.com")
                .mutedEventTypes(List.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(preferenceService.getPreference(userId)).thenReturn(dto);

        mockMvc.perform(get("/api/notifications/preferences/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enableEmail").value(true))
                .andExpect(jsonPath("$.data.enableSlack").value(false))
                .andExpect(jsonPath("$.data.emailAddress").value("user@example.com"));
    }

    @Test
    void should_updatePreferences_when_called() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        UpdatePreferenceRequest request = UpdatePreferenceRequest.builder()
                .enableSlack(true)
                .slackWebhookUrl("https://hooks.slack.com/test")
                .build();

        NotificationPreferenceDto dto = NotificationPreferenceDto.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tenantId(tenantId)
                .enableEmail(true)
                .enableSlack(true)
                .enableTeams(false)
                .enableInApp(true)
                .slackWebhookUrl("https://hooks.slack.com/test")
                .mutedEventTypes(List.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(preferenceService.createOrUpdatePreference(eq(userId), eq(tenantId), any(UpdatePreferenceRequest.class)))
                .thenReturn(dto);

        mockMvc.perform(put("/api/notifications/preferences/user/{userId}", userId)
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enableSlack").value(true))
                .andExpect(jsonPath("$.data.slackWebhookUrl").value("https://hooks.slack.com/test"));
    }
}
