package com.squadron.agent.controller;

import com.squadron.agent.config.SecurityConfig;
import com.squadron.agent.dto.ActiveAgentWorkDto;
import com.squadron.agent.dto.AgentActivityDto;
import com.squadron.agent.dto.AgentDashboardDto;
import com.squadron.agent.dto.AgentTypeSummaryDto;
import com.squadron.agent.service.AgentDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AgentDashboardController.class)
@ContextConfiguration(classes = {AgentDashboardController.class, SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class AgentDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentDashboardService dashboardService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/agents/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_returnDashboard_when_authenticatedAsDeveloper() throws Exception {
        AgentDashboardDto dto = buildMockDashboard();
        when(dashboardService.getDashboard(nullable(UUID.class))).thenReturn(dto);

        mockMvc.perform(get("/api/agents/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activeAgents").value(2))
                .andExpect(jsonPath("$.data.idleAgents").value(4))
                .andExpect(jsonPath("$.data.totalConversations").value(5))
                .andExpect(jsonPath("$.data.totalTokensUsed").value(3000));
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_returnDashboard_when_authenticatedAsAdmin() throws Exception {
        AgentDashboardDto dto = buildMockDashboard();
        when(dashboardService.getDashboard(nullable(UUID.class))).thenReturn(dto);

        mockMvc.perform(get("/api/agents/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activeAgents").value(2));
    }

    @Test
    @WithMockUser(roles = {"team-lead"})
    void should_returnDashboard_when_authenticatedAsTeamLead() throws Exception {
        AgentDashboardDto dto = buildMockDashboard();
        when(dashboardService.getDashboard(nullable(UUID.class))).thenReturn(dto);

        mockMvc.perform(get("/api/agents/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activeAgents").value(2));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_returnActiveWorkItems_when_agentsWorking() throws Exception {
        AgentDashboardDto dto = buildMockDashboard();
        when(dashboardService.getDashboard(nullable(UUID.class))).thenReturn(dto);

        mockMvc.perform(get("/api/agents/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeWork.length()").value(2))
                .andExpect(jsonPath("$.data.activeWork[0].agentType").value("CODING"))
                .andExpect(jsonPath("$.data.activeWork[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.activeWork[1].agentType").value("REVIEW"));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_returnRecentActivity_when_conversationsExist() throws Exception {
        AgentDashboardDto dto = buildMockDashboard();
        when(dashboardService.getDashboard(nullable(UUID.class))).thenReturn(dto);

        mockMvc.perform(get("/api/agents/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentActivity.length()").value(3))
                .andExpect(jsonPath("$.data.recentActivity[0].agentType").value("CODING"))
                .andExpect(jsonPath("$.data.recentActivity[0].action").value("working"));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_returnAgentTypeSummaries_when_dataAvailable() throws Exception {
        AgentDashboardDto dto = buildMockDashboard();
        when(dashboardService.getDashboard(nullable(UUID.class))).thenReturn(dto);

        mockMvc.perform(get("/api/agents/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentTypeSummaries.length()").value(2))
                .andExpect(jsonPath("$.data.agentTypeSummaries[0].agentType").value("CODING"))
                .andExpect(jsonPath("$.data.agentTypeSummaries[0].activeCount").value(1))
                .andExpect(jsonPath("$.data.agentTypeSummaries[0].completedCount").value(2))
                .andExpect(jsonPath("$.data.agentTypeSummaries[0].totalTokens").value(1500));

        verify(dashboardService).getDashboard(nullable(UUID.class));
    }

    private AgentDashboardDto buildMockDashboard() {
        Instant now = Instant.now();
        UUID taskId1 = UUID.randomUUID();
        UUID taskId2 = UUID.randomUUID();

        return AgentDashboardDto.builder()
                .activeAgents(2)
                .idleAgents(4)
                .totalConversations(5)
                .totalTokensUsed(3000L)
                .activeWork(List.of(
                        ActiveAgentWorkDto.builder()
                                .conversationId(UUID.randomUUID())
                                .taskId(taskId1)
                                .agentType("CODING")
                                .status("ACTIVE")
                                .provider("openai")
                                .model("gpt-4o")
                                .totalTokens(1200L)
                                .startedAt(now.minusSeconds(600))
                                .lastActivityAt(now.minusSeconds(10))
                                .build(),
                        ActiveAgentWorkDto.builder()
                                .conversationId(UUID.randomUUID())
                                .taskId(taskId2)
                                .agentType("REVIEW")
                                .status("ACTIVE")
                                .provider("openai")
                                .model("gpt-4o")
                                .totalTokens(300L)
                                .startedAt(now.minusSeconds(300))
                                .lastActivityAt(now.minusSeconds(5))
                                .build()
                ))
                .recentActivity(List.of(
                        AgentActivityDto.builder()
                                .conversationId(UUID.randomUUID())
                                .taskId(taskId1)
                                .agentType("CODING")
                                .action("working")
                                .totalTokens(1200L)
                                .timestamp(now.minusSeconds(10))
                                .build(),
                        AgentActivityDto.builder()
                                .conversationId(UUID.randomUUID())
                                .taskId(taskId2)
                                .agentType("REVIEW")
                                .action("working")
                                .totalTokens(300L)
                                .timestamp(now.minusSeconds(5))
                                .build(),
                        AgentActivityDto.builder()
                                .conversationId(UUID.randomUUID())
                                .taskId(UUID.randomUUID())
                                .agentType("PLANNING")
                                .action("completed")
                                .totalTokens(500L)
                                .timestamp(now.minusSeconds(3600))
                                .build()
                ))
                .agentTypeSummaries(List.of(
                        AgentTypeSummaryDto.builder()
                                .agentType("CODING")
                                .activeCount(1)
                                .completedCount(2)
                                .totalTokens(1500L)
                                .build(),
                        AgentTypeSummaryDto.builder()
                                .agentType("REVIEW")
                                .activeCount(1)
                                .completedCount(0)
                                .totalTokens(300L)
                                .build()
                ))
                .build();
    }
}
