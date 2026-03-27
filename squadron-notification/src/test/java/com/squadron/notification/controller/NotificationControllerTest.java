package com.squadron.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squadron.notification.dto.NotificationDto;
import com.squadron.notification.dto.SendNotificationRequest;
import com.squadron.notification.service.NotificationService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        NotificationController controller = new NotificationController(notificationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void should_sendNotification_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SendNotificationRequest request = SendNotificationRequest.builder()
                .tenantId(tenantId)
                .userId(userId)
                .channel("IN_APP")
                .subject("Test Subject")
                .body("Test Body")
                .build();

        NotificationDto response = NotificationDto.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(userId)
                .channel("IN_APP")
                .subject("Test Subject")
                .body("Test Body")
                .status("SENT")
                .createdAt(Instant.now())
                .build();

        when(notificationService.sendNotification(any(SendNotificationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.channel").value("IN_APP"))
                .andExpect(jsonPath("$.data.status").value("SENT"));
    }

    @Test
    void should_listNotifications_when_called() throws Exception {
        UUID userId = UUID.randomUUID();
        NotificationDto dto = NotificationDto.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .channel("IN_APP")
                .subject("Test")
                .body("Body")
                .status("SENT")
                .createdAt(Instant.now())
                .build();

        when(notificationService.listNotifications(userId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/notifications/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void should_listUnread_when_called() throws Exception {
        UUID userId = UUID.randomUUID();
        when(notificationService.listUnread(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications/user/{userId}/unread", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void should_countUnread_when_called() throws Exception {
        UUID userId = UUID.randomUUID();
        when(notificationService.countUnread(userId)).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/user/{userId}/unread/count", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(3));
    }

    @Test
    void should_markAsRead_when_called() throws Exception {
        UUID id = UUID.randomUUID();
        NotificationDto dto = NotificationDto.builder()
                .id(id)
                .channel("IN_APP")
                .subject("Test")
                .body("Body")
                .status("READ")
                .readAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        when(notificationService.markAsRead(id)).thenReturn(dto);

        mockMvc.perform(put("/api/notifications/{id}/read", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("READ"));
    }

    @Test
    void should_markAllRead_when_called() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/notifications/user/{userId}/read-all", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).markAllRead(userId);
    }
}
