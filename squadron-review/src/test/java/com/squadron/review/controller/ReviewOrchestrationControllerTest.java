package com.squadron.review.controller;

import com.squadron.review.config.SecurityConfig;
import com.squadron.review.service.ReviewOrchestrationService;
import com.squadron.review.service.ReviewOrchestrationService.ReviewOrchestrationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReviewOrchestrationController.class)
@ContextConfiguration(classes = {ReviewOrchestrationController.class, SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class ReviewOrchestrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewOrchestrationService reviewOrchestrationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(roles = {"developer"})
    void should_orchestrateReview() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ReviewOrchestrationResult result = ReviewOrchestrationResult.builder()
                .taskId(taskId)
                .createdReviewIds(List.of(UUID.randomUUID(), UUID.randomUUID()))
                .aiReviewCreated(true)
                .pendingHumanReviews(0)
                .policyMet(false)
                .build();

        when(reviewOrchestrationService.orchestrateReview(taskId, tenantId, teamId)).thenReturn(result);

        mockMvc.perform(post("/api/reviews/orchestration/task/{taskId}", taskId)
                        .with(csrf())
                        .param("tenantId", tenantId.toString())
                        .param("teamId", teamId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.data.aiReviewCreated").value(true))
                .andExpect(jsonPath("$.data.createdReviewIds").isArray())
                .andExpect(jsonPath("$.data.createdReviewIds.length()").value(2));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_checkAndTransition() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(reviewOrchestrationService.checkAndTransition(taskId, tenantId, null)).thenReturn(true);

        mockMvc.perform(get("/api/reviews/orchestration/task/{taskId}/check", taskId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void should_requireAuthentication() throws Exception {
        mockMvc.perform(post("/api/reviews/orchestration/task/{taskId}", UUID.randomUUID())
                        .with(csrf())
                        .param("tenantId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }
}
