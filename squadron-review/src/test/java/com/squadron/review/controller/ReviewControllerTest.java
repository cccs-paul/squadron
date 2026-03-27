package com.squadron.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.review.dto.CreateReviewRequest;
import com.squadron.review.dto.ReviewCommentDto;
import com.squadron.review.dto.ReviewDto;
import com.squadron.review.dto.ReviewSummary;
import com.squadron.review.dto.SubmitReviewRequest;
import com.squadron.review.entity.Review;
import com.squadron.review.service.ReviewGateService;
import com.squadron.review.service.ReviewService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReviewController.class)
@ContextConfiguration(classes = {ReviewController.class, com.squadron.review.config.SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private ReviewGateService reviewGateService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_createReview_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        CreateReviewRequest request = CreateReviewRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerType("HUMAN")
                .build();

        Review savedReview = Review.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerType("HUMAN")
                .status("PENDING")
                .build();

        when(reviewService.createReview(any(CreateReviewRequest.class))).thenReturn(savedReview);

        mockMvc.perform(post("/api/reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_submitReview_when_validRequest() throws Exception {
        UUID reviewId = UUID.randomUUID();

        SubmitReviewRequest request = SubmitReviewRequest.builder()
                .reviewId(reviewId)
                .status("APPROVED")
                .summary("LGTM")
                .comments(List.of(ReviewCommentDto.builder()
                        .body("Looks good")
                        .severity("INFO")
                        .build()))
                .build();

        ReviewDto responseDto = ReviewDto.builder()
                .id(reviewId)
                .status("APPROVED")
                .summary("LGTM")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .comments(List.of(ReviewCommentDto.builder()
                        .body("Looks good")
                        .severity("INFO")
                        .build()))
                .build();

        when(reviewService.submitReview(any(SubmitReviewRequest.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/reviews/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_getReview_when_exists() throws Exception {
        UUID reviewId = UUID.randomUUID();

        ReviewDto dto = ReviewDto.builder()
                .id(reviewId)
                .reviewerType("HUMAN")
                .status("APPROVED")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .comments(List.of())
                .build();

        when(reviewService.getReview(reviewId)).thenReturn(dto);

        mockMvc.perform(get("/api/reviews/{id}", reviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(reviewId.toString()));
    }

    @Test
    @WithMockUser(roles = {"qa"})
    void should_listReviewsForTask() throws Exception {
        UUID taskId = UUID.randomUUID();

        ReviewDto dto = ReviewDto.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .reviewerType("AI")
                .status("PENDING")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .comments(List.of())
                .build();

        when(reviewService.listReviewsForTask(taskId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/reviews/task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].taskId").value(taskId.toString()));
    }

    @Test
    @WithMockUser(roles = {"qa"})
    void should_deleteReview() throws Exception {
        UUID reviewId = UUID.randomUUID();

        doNothing().when(reviewService).deleteReview(reviewId);

        mockMvc.perform(delete("/api/reviews/{id}", reviewId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(reviewService).deleteReview(reviewId);
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_checkReviewGate() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ReviewSummary summary = ReviewSummary.builder()
                .taskId(taskId)
                .totalReviews(2)
                .humanApprovals(1)
                .aiApproval(true)
                .policyMet(true)
                .reviews(List.of())
                .build();

        when(reviewGateService.checkReviewGate(taskId, tenantId, teamId)).thenReturn(summary);

        mockMvc.perform(get("/api/reviews/task/{taskId}/gate", taskId)
                        .param("tenantId", tenantId.toString())
                        .param("teamId", teamId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.policyMet").value(true))
                .andExpect(jsonPath("$.data.totalReviews").value(2));
    }

    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/reviews/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
