package com.squadron.agent.controller;

import com.squadron.agent.config.SecurityConfig;
import com.squadron.agent.dto.UsageByAgentDto;
import com.squadron.agent.dto.UsageSummaryDto;
import com.squadron.agent.service.TokenUsageService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TokenUsageController.class)
@ContextConfiguration(classes = {TokenUsageController.class, SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class TokenUsageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenUsageService tokenUsageService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void should_getTenantSummary_when_unauthenticated() throws Exception {
        UUID tenantId = UUID.randomUUID();

        UsageSummaryDto summary = UsageSummaryDto.builder()
                .totalInputTokens(5000)
                .totalOutputTokens(2500)
                .totalTokens(7500)
                .totalCost(0.05)
                .invocations(3)
                .build();

        when(tokenUsageService.getTenantSummary(eq(tenantId), isNull(), isNull()))
                .thenReturn(summary);

        mockMvc.perform(get("/api/agent/usage/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalInputTokens").value(5000))
                .andExpect(jsonPath("$.data.totalOutputTokens").value(2500))
                .andExpect(jsonPath("$.data.totalTokens").value(7500))
                .andExpect(jsonPath("$.data.totalCost").value(0.05))
                .andExpect(jsonPath("$.data.invocations").value(3));

        verify(tokenUsageService).getTenantSummary(eq(tenantId), isNull(), isNull());
    }

    @Test
    void should_getTenantSummary_withDateRange() throws Exception {
        UUID tenantId = UUID.randomUUID();

        UsageSummaryDto summary = UsageSummaryDto.builder()
                .totalInputTokens(1000)
                .totalOutputTokens(500)
                .totalTokens(1500)
                .totalCost(0.01)
                .invocations(1)
                .build();

        when(tokenUsageService.getTenantSummary(eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(summary);

        mockMvc.perform(get("/api/agent/usage/tenant/{tenantId}", tenantId)
                        .param("start", "2026-01-01T00:00:00Z")
                        .param("end", "2026-03-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalTokens").value(1500))
                .andExpect(jsonPath("$.data.invocations").value(1));
    }

    @Test
    void should_getUserSummary_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        UsageSummaryDto summary = UsageSummaryDto.builder()
                .totalInputTokens(2000)
                .totalOutputTokens(1000)
                .totalTokens(3000)
                .totalCost(0.02)
                .invocations(2)
                .build();

        when(tokenUsageService.getUserSummary(eq(tenantId), eq(userId), isNull(), isNull()))
                .thenReturn(summary);

        mockMvc.perform(get("/api/agent/usage/tenant/{tenantId}/user/{userId}", tenantId, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalInputTokens").value(2000))
                .andExpect(jsonPath("$.data.totalOutputTokens").value(1000))
                .andExpect(jsonPath("$.data.totalTokens").value(3000))
                .andExpect(jsonPath("$.data.invocations").value(2));

        verify(tokenUsageService).getUserSummary(eq(tenantId), eq(userId), isNull(), isNull());
    }

    @Test
    void should_getTeamSummary_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        UsageSummaryDto summary = UsageSummaryDto.builder()
                .totalInputTokens(10000)
                .totalOutputTokens(5000)
                .totalTokens(15000)
                .totalCost(0.10)
                .invocations(5)
                .build();

        when(tokenUsageService.getTeamSummary(tenantId, teamId)).thenReturn(summary);

        mockMvc.perform(get("/api/agent/usage/tenant/{tenantId}/team/{teamId}", tenantId, teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalInputTokens").value(10000))
                .andExpect(jsonPath("$.data.totalOutputTokens").value(5000))
                .andExpect(jsonPath("$.data.totalTokens").value(15000))
                .andExpect(jsonPath("$.data.invocations").value(5));

        verify(tokenUsageService).getTeamSummary(tenantId, teamId);
    }

    @Test
    void should_getByAgentType_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();

        List<UsageByAgentDto> usages = List.of(
                UsageByAgentDto.builder()
                        .agentType("CODING")
                        .totalTokens(5000)
                        .totalCost(0.03)
                        .invocations(2)
                        .build(),
                UsageByAgentDto.builder()
                        .agentType("REVIEW")
                        .totalTokens(3000)
                        .totalCost(0.02)
                        .invocations(1)
                        .build()
        );

        when(tokenUsageService.getUsageByAgentType(tenantId)).thenReturn(usages);

        mockMvc.perform(get("/api/agent/usage/tenant/{tenantId}/by-agent", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].agentType").value("CODING"))
                .andExpect(jsonPath("$.data[0].totalTokens").value(5000))
                .andExpect(jsonPath("$.data[0].totalCost").value(0.03))
                .andExpect(jsonPath("$.data[0].invocations").value(2))
                .andExpect(jsonPath("$.data[1].agentType").value("REVIEW"))
                .andExpect(jsonPath("$.data[1].totalTokens").value(3000));

        verify(tokenUsageService).getUsageByAgentType(tenantId);
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getTenantSummary_when_authenticated() throws Exception {
        UUID tenantId = UUID.randomUUID();

        UsageSummaryDto summary = UsageSummaryDto.builder()
                .totalInputTokens(100)
                .totalOutputTokens(50)
                .totalTokens(150)
                .totalCost(0.001)
                .invocations(1)
                .build();

        when(tokenUsageService.getTenantSummary(eq(tenantId), isNull(), isNull()))
                .thenReturn(summary);

        mockMvc.perform(get("/api/agent/usage/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalTokens").value(150));
    }
}
