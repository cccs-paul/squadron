package com.squadron.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.config.SecurityConfig;
import com.squadron.agent.dto.PlanApprovalRequest;
import com.squadron.agent.entity.TaskPlan;
import com.squadron.agent.service.PlanService;
import com.squadron.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlanController.class)
@ContextConfiguration(classes = {PlanController.class, SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlanService planService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        TenantContext.setContext(TenantContext.builder()
                .tenantId(tenantId)
                .userId(userId)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_listPlans_when_authenticated() throws Exception {
        UUID taskId = UUID.randomUUID();

        List<TaskPlan> plans = List.of(
                TaskPlan.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenantId)
                        .taskId(taskId)
                        .version(2)
                        .planContent("Updated plan")
                        .status("DRAFT")
                        .build(),
                TaskPlan.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenantId)
                        .taskId(taskId)
                        .version(1)
                        .planContent("Initial plan")
                        .status("APPROVED")
                        .build()
        );

        when(planService.listPlans(taskId)).thenReturn(plans);

        mockMvc.perform(get("/api/agents/plans/task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[0].planContent").value("Updated plan"))
                .andExpect(jsonPath("$.data[1].version").value(1))
                .andExpect(jsonPath("$.data[1].status").value("APPROVED"));

        verify(planService).listPlans(taskId);
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_listPlans_when_anyAuthenticatedUser() throws Exception {
        UUID taskId = UUID.randomUUID();

        when(planService.listPlans(taskId)).thenReturn(List.of());

        mockMvc.perform(get("/api/agents/plans/task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getLatestPlan_when_exists() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        TaskPlan plan = TaskPlan.builder()
                .id(planId)
                .tenantId(tenantId)
                .taskId(taskId)
                .version(3)
                .planContent("Latest plan content")
                .status("DRAFT")
                .build();

        when(planService.getLatestPlan(taskId)).thenReturn(plan);

        mockMvc.perform(get("/api/agents/plans/task/{taskId}/latest", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(planId.toString()))
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.planContent").value("Latest plan content"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(planService).getLatestPlan(taskId);
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_approvePlan_when_adminRole() throws Exception {
        UUID planId = UUID.randomUUID();

        PlanApprovalRequest request = PlanApprovalRequest.builder()
                .planId(planId)
                .approved(true)
                .build();

        TaskPlan approvedPlan = TaskPlan.builder()
                .id(planId)
                .tenantId(tenantId)
                .taskId(UUID.randomUUID())
                .version(1)
                .planContent("Plan content")
                .status("APPROVED")
                .approvedBy(userId)
                .approvedAt(Instant.now())
                .build();

        when(planService.approvePlan(eq(planId), any(UUID.class))).thenReturn(approvedPlan);

        mockMvc.perform(post("/api/agents/plans/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(planId.toString()))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedBy").value(userId.toString()));

        verify(planService).approvePlan(eq(planId), any(UUID.class));
    }

    @Test
    @WithMockUser(roles = {"team-lead"})
    void should_approvePlan_when_teamLeadRole() throws Exception {
        UUID planId = UUID.randomUUID();

        PlanApprovalRequest request = PlanApprovalRequest.builder()
                .planId(planId)
                .approved(true)
                .build();

        TaskPlan approvedPlan = TaskPlan.builder()
                .id(planId)
                .tenantId(tenantId)
                .taskId(UUID.randomUUID())
                .version(1)
                .planContent("Plan content")
                .status("APPROVED")
                .approvedBy(userId)
                .build();

        when(planService.approvePlan(eq(planId), any(UUID.class))).thenReturn(approvedPlan);

        mockMvc.perform(post("/api/agents/plans/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_return403_when_developerTriesToApprovePlan() throws Exception {
        PlanApprovalRequest request = PlanApprovalRequest.builder()
                .planId(UUID.randomUUID())
                .approved(true)
                .build();

        mockMvc.perform(post("/api/agents/plans/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/agents/plans/task/{taskId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return401_when_approvingUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/agents/plans/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + UUID.randomUUID() + "\",\"approved\":true}"))
                .andExpect(status().isUnauthorized());
    }
}
