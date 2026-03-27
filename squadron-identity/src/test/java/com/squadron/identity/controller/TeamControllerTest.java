package com.squadron.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.dto.TeamDto;
import com.squadron.identity.exception.GlobalExceptionHandler;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TeamControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TeamService teamService;

    @InjectMocks
    private TeamController teamController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(teamController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void should_return201_when_createTeamSuccessful() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        TeamDto created = TeamDto.builder()
                .id(teamId)
                .tenantId(tenantId)
                .name("Engineering")
                .createdAt(Instant.now())
                .build();
        when(teamService.createTeam(eq(tenantId), any(TeamDto.class))).thenReturn(created);

        TeamDto request = TeamDto.builder()
                .tenantId(tenantId)
                .name("Engineering")
                .build();

        mockMvc.perform(post("/api/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Engineering"));
    }

    @Test
    void should_returnTeams_when_listByTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TeamDto t1 = TeamDto.builder().id(UUID.randomUUID()).name("Engineering").build();
        TeamDto t2 = TeamDto.builder().id(UUID.randomUUID()).name("Design").build();
        when(teamService.listTeamsByTenant(tenantId)).thenReturn(List.of(t1, t2));

        mockMvc.perform(get("/api/teams/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Engineering"));
    }

    @Test
    void should_returnTeam_when_getById() throws Exception {
        UUID teamId = UUID.randomUUID();
        TeamDto team = TeamDto.builder().id(teamId).name("Engineering").build();
        when(teamService.getTeam(teamId)).thenReturn(team);

        mockMvc.perform(get("/api/teams/{id}", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Engineering"));
    }

    @Test
    void should_return404_when_teamNotFound() throws Exception {
        UUID teamId = UUID.randomUUID();
        when(teamService.getTeam(teamId)).thenThrow(new ResourceNotFoundException("Team", "id", teamId));

        mockMvc.perform(get("/api/teams/{id}", teamId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void should_returnUpdatedTeam_when_updateSuccessful() throws Exception {
        UUID teamId = UUID.randomUUID();
        TeamDto updated = TeamDto.builder()
                .id(teamId)
                .name("Updated Engineering")
                .build();
        when(teamService.updateTeam(eq(teamId), any(TeamDto.class))).thenReturn(updated);

        TeamDto request = TeamDto.builder().name("Updated Engineering").build();

        mockMvc.perform(put("/api/teams/{id}", teamId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated Engineering"));
    }

    @Test
    void should_return200_when_deleteTeamSuccessful() throws Exception {
        UUID teamId = UUID.randomUUID();
        doNothing().when(teamService).deleteTeam(teamId);

        mockMvc.perform(delete("/api/teams/{id}", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(teamService).deleteTeam(teamId);
    }
}
