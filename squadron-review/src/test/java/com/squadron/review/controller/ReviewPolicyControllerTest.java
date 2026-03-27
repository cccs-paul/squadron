package com.squadron.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.review.dto.ReviewPolicyDto;
import com.squadron.review.entity.ReviewPolicy;
import com.squadron.review.service.ReviewPolicyService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReviewPolicyController.class)
@ContextConfiguration(classes = {ReviewPolicyController.class, com.squadron.review.config.SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class ReviewPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewPolicyService reviewPolicyService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_createPolicy_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ReviewPolicyDto dto = ReviewPolicyDto.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .minHumanApprovals(2)
                .requireAiReview(true)
                .build();

        ReviewPolicy savedPolicy = ReviewPolicy.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .minHumanApprovals(2)
                .requireAiReview(true)
                .selfReviewAllowed(true)
                .build();

        when(reviewPolicyService.createOrUpdatePolicy(any(ReviewPolicyDto.class))).thenReturn(savedPolicy);

        mockMvc.perform(post("/api/reviews/policies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.minHumanApprovals").value(2));
    }

    @Test
    @WithMockUser(roles = {"team-lead"})
    void should_resolvePolicy() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ReviewPolicy policy = ReviewPolicy.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .minHumanApprovals(1)
                .requireAiReview(true)
                .selfReviewAllowed(true)
                .build();

        when(reviewPolicyService.resolvePolicy(tenantId, teamId)).thenReturn(policy);

        mockMvc.perform(get("/api/reviews/policies/resolve")
                        .param("tenantId", tenantId.toString())
                        .param("teamId", teamId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantId").value(tenantId.toString()));
    }

    @Test
    @WithMockUser(roles = {"team-lead"})
    void should_getPolicy() throws Exception {
        UUID policyId = UUID.randomUUID();

        ReviewPolicy policy = ReviewPolicy.builder()
                .id(policyId)
                .tenantId(UUID.randomUUID())
                .minHumanApprovals(1)
                .requireAiReview(true)
                .selfReviewAllowed(true)
                .build();

        when(reviewPolicyService.getPolicy(policyId)).thenReturn(policy);

        mockMvc.perform(get("/api/reviews/policies/{id}", policyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(policyId.toString()));
    }

    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/reviews/policies/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
