package com.squadron.git.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.git.dto.CreatePullRequestRequest;
import com.squadron.git.dto.DiffFile;
import com.squadron.git.dto.DiffResult;
import com.squadron.git.dto.MergeRequest;
import com.squadron.git.dto.MergeabilityDto;
import com.squadron.git.dto.PullRequestDto;
import com.squadron.git.service.PullRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PullRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
class PullRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PullRequestService pullRequestService;

    @Test
    void should_createPullRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID prId = UUID.randomUUID();

        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .repoOwner("owner")
                .repoName("repo")
                .title("My PR")
                .description("Description")
                .sourceBranch("feature")
                .targetBranch("main")
                .accessToken("token")
                .build();

        PullRequestDto response = PullRequestDto.builder()
                .id(prId)
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .externalPrId("42")
                .externalPrUrl("https://github.com/owner/repo/pull/42")
                .title("My PR")
                .sourceBranch("feature")
                .targetBranch("main")
                .status("OPEN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(pullRequestService.createPullRequest(any(CreatePullRequestRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/git/pull-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(prId.toString()))
                .andExpect(jsonPath("$.data.externalPrId").value("42"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void should_mergePullRequest() throws Exception {
        UUID prId = UUID.randomUUID();
        MergeRequest request = MergeRequest.builder()
                .pullRequestRecordId(prId)
                .mergeStrategy("SQUASH")
                .accessToken("token")
                .build();

        doNothing().when(pullRequestService).mergePullRequest(any(MergeRequest.class));

        mockMvc.perform(post("/api/git/pull-requests/{id}/merge", prId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(pullRequestService).mergePullRequest(any(MergeRequest.class));
    }

    @Test
    void should_getPullRequest() throws Exception {
        UUID prId = UUID.randomUUID();
        PullRequestDto response = PullRequestDto.builder()
                .id(prId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .platform("GITLAB")
                .externalPrId("10")
                .title("MR Title")
                .sourceBranch("dev")
                .targetBranch("main")
                .status("OPEN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(pullRequestService.getPullRequest(prId)).thenReturn(response);

        mockMvc.perform(get("/api/git/pull-requests/{id}", prId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(prId.toString()))
                .andExpect(jsonPath("$.data.platform").value("GITLAB"));
    }

    @Test
    void should_listPullRequests() throws Exception {
        UUID tenantId = UUID.randomUUID();
        PullRequestDto pr1 = PullRequestDto.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(UUID.randomUUID())
                .platform("GITHUB")
                .externalPrId("1")
                .title("PR 1")
                .sourceBranch("feat1")
                .targetBranch("main")
                .status("OPEN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(pullRequestService.listPullRequestsByTenant(tenantId, "OPEN")).thenReturn(List.of(pr1));

        mockMvc.perform(get("/api/git/pull-requests/tenant/{tenantId}", tenantId)
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void should_requestReviewers() throws Exception {
        UUID prId = UUID.randomUUID();
        List<String> reviewers = List.of("user1", "user2");

        doNothing().when(pullRequestService).requestReviewers(eq(prId), eq(reviewers), eq("token"));

        mockMvc.perform(post("/api/git/pull-requests/{id}/reviewers", prId)
                        .param("accessToken", "token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewers)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(pullRequestService).requestReviewers(prId, reviewers, "token");
    }

    @Test
    void should_getDiff() throws Exception {
        UUID prId = UUID.randomUUID();
        DiffResult diff = DiffResult.builder()
                .files(List.of(
                        DiffFile.builder().filename("src/Main.java").status("modified").additions(10).deletions(2).build()
                ))
                .totalAdditions(10)
                .totalDeletions(2)
                .build();

        when(pullRequestService.getDiff(prId, "token")).thenReturn(diff);

        mockMvc.perform(get("/api/git/pull-requests/{id}/diff", prId)
                        .param("accessToken", "token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAdditions").value(10))
                .andExpect(jsonPath("$.data.totalDeletions").value(2))
                .andExpect(jsonPath("$.data.files", hasSize(1)));
    }

    // ---- Tests for GET /task/{taskId} ----

    @Test
    void should_getByTaskId() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID prId = UUID.randomUUID();
        PullRequestDto response = PullRequestDto.builder()
                .id(prId)
                .tenantId(UUID.randomUUID())
                .taskId(taskId)
                .platform("GITHUB")
                .externalPrId("42")
                .externalPrUrl("https://github.com/owner/repo/pull/42")
                .title("My PR")
                .sourceBranch("feature")
                .targetBranch("main")
                .status("OPEN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(pullRequestService.getByTaskId(taskId)).thenReturn(response);

        mockMvc.perform(get("/api/git/pull-requests/task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(prId.toString()))
                .andExpect(jsonPath("$.data.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.data.externalPrId").value("42"));
    }

    // ---- Tests for GET /{id}/mergeability ----

    @Test
    void should_checkMergeability_returnsMergeable() throws Exception {
        UUID prId = UUID.randomUUID();
        MergeabilityDto response = MergeabilityDto.builder()
                .mergeable(true)
                .conflictFiles(List.of())
                .build();

        when(pullRequestService.checkMergeability(prId)).thenReturn(response);

        mockMvc.perform(get("/api/git/pull-requests/{id}/mergeability", prId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mergeable").value(true))
                .andExpect(jsonPath("$.data.conflictFiles", hasSize(0)));
    }

    @Test
    void should_checkMergeability_returnsNotMergeable() throws Exception {
        UUID prId = UUID.randomUUID();
        MergeabilityDto response = MergeabilityDto.builder()
                .mergeable(false)
                .conflictFiles(List.of("file1.java", "file2.java"))
                .build();

        when(pullRequestService.checkMergeability(prId)).thenReturn(response);

        mockMvc.perform(get("/api/git/pull-requests/{id}/mergeability", prId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mergeable").value(false))
                .andExpect(jsonPath("$.data.conflictFiles", hasSize(2)));
    }
}
