package com.squadron.platform.controller;

import com.squadron.platform.config.SecurityConfig;
import com.squadron.platform.dto.PlatformTaskDto;
import com.squadron.platform.service.PlatformSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlatformSyncController.class)
@ContextConfiguration(classes = {PlatformSyncController.class, SecurityConfig.class})
class PlatformSyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformSyncService syncService;

    @MockBean
    private JwtDecoder jwtDecoder;

    // --- POST /api/platforms/sync/{connectionId}/tasks ---

    @Test
    @WithMockUser
    void should_syncTasks_when_authenticated() throws Exception {
        UUID connectionId = UUID.randomUUID();
        String projectKey = "PROJ";

        PlatformTaskDto task = PlatformTaskDto.builder()
                .externalId("PROJ-1")
                .title("Fix bug")
                .status("OPEN")
                .build();

        when(syncService.syncTasks(connectionId, projectKey)).thenReturn(List.of(task));

        mockMvc.perform(post("/api/platforms/sync/{connectionId}/tasks", connectionId)
                        .param("projectKey", projectKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].externalId").value("PROJ-1"))
                .andExpect(jsonPath("$.data[0].title").value("Fix bug"));
    }

    @Test
    @WithMockUser
    void should_syncTasks_returnEmptyList() throws Exception {
        UUID connectionId = UUID.randomUUID();

        when(syncService.syncTasks(connectionId, "EMPTY")).thenReturn(List.of());

        mockMvc.perform(post("/api/platforms/sync/{connectionId}/tasks", connectionId)
                        .param("projectKey", "EMPTY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void should_return401_when_syncTasksUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/platforms/sync/{connectionId}/tasks", UUID.randomUUID())
                        .param("projectKey", "PROJ"))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /api/platforms/sync/{connectionId}/push-status ---

    @Test
    @WithMockUser
    void should_pushStatus_when_authenticated() throws Exception {
        UUID connectionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        doNothing().when(syncService).pushTaskStatus(connectionId, "PROJ-1", "DONE", "Completed", userId);

        mockMvc.perform(post("/api/platforms/sync/{connectionId}/push-status", connectionId)
                        .param("externalId", "PROJ-1")
                        .param("status", "DONE")
                        .param("comment", "Completed")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(syncService).pushTaskStatus(connectionId, "PROJ-1", "DONE", "Completed", userId);
    }

    @Test
    @WithMockUser
    void should_pushStatus_when_optionalParamsOmitted() throws Exception {
        UUID connectionId = UUID.randomUUID();

        doNothing().when(syncService).pushTaskStatus(connectionId, "PROJ-2", "IN_PROGRESS", null, null);

        mockMvc.perform(post("/api/platforms/sync/{connectionId}/push-status", connectionId)
                        .param("externalId", "PROJ-2")
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(syncService).pushTaskStatus(connectionId, "PROJ-2", "IN_PROGRESS", null, null);
    }

    @Test
    void should_return401_when_pushStatusUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/platforms/sync/{connectionId}/push-status", UUID.randomUUID())
                        .param("externalId", "PROJ-1")
                        .param("status", "DONE"))
                .andExpect(status().isUnauthorized());
    }
}
