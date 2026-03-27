package com.squadron.git.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.git.dto.BranchRequest;
import com.squadron.git.dto.CloneRequest;
import com.squadron.git.dto.CommitRequest;
import com.squadron.git.dto.GitCommandResult;
import com.squadron.git.dto.PushRequest;
import com.squadron.git.entity.GitOperation;
import com.squadron.git.service.GitOperationService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitOperationController.class)
@AutoConfigureMockMvc(addFilters = false)
class GitOperationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GitOperationService gitOperationService;

    @Test
    void should_cloneRepo() throws Exception {
        UUID taskId = UUID.randomUUID();
        CloneRequest request = CloneRequest.builder()
                .workspaceId(UUID.randomUUID())
                .repoUrl("https://github.com/owner/repo.git")
                .branch("main")
                .build();

        GitCommandResult result = GitCommandResult.builder()
                .success(true)
                .output("Cloning into '.'...")
                .errorOutput("")
                .exitCode(0)
                .build();

        when(gitOperationService.cloneRepo(eq(taskId), any(CloneRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/git/operations/clone")
                        .param("taskId", taskId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.exitCode").value(0));
    }

    @Test
    void should_createBranch() throws Exception {
        UUID taskId = UUID.randomUUID();
        BranchRequest request = BranchRequest.builder()
                .workspaceId(UUID.randomUUID())
                .branchName("feature/new")
                .build();

        GitCommandResult result = GitCommandResult.builder()
                .success(true)
                .output("Switched to new branch 'feature/new'")
                .errorOutput("")
                .exitCode(0)
                .build();

        when(gitOperationService.createBranch(eq(taskId), any(BranchRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/git/operations/branch")
                        .param("taskId", taskId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    void should_commit() throws Exception {
        UUID taskId = UUID.randomUUID();
        CommitRequest request = CommitRequest.builder()
                .workspaceId(UUID.randomUUID())
                .message("Fix bug")
                .authorName("John")
                .authorEmail("john@test.com")
                .build();

        GitCommandResult result = GitCommandResult.builder()
                .success(true)
                .output("[main abc1234] Fix bug")
                .errorOutput("")
                .exitCode(0)
                .build();

        when(gitOperationService.commit(eq(taskId), any(CommitRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/git/operations/commit")
                        .param("taskId", taskId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    void should_push() throws Exception {
        UUID taskId = UUID.randomUUID();
        PushRequest request = PushRequest.builder()
                .workspaceId(UUID.randomUUID())
                .branch("main")
                .build();

        GitCommandResult result = GitCommandResult.builder()
                .success(true)
                .output("Everything up-to-date")
                .errorOutput("")
                .exitCode(0)
                .build();

        when(gitOperationService.push(eq(taskId), any(PushRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/git/operations/push")
                        .param("taskId", taskId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    void should_listOperationsForTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        GitOperation op1 = GitOperation.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .taskId(taskId)
                .workspaceId(UUID.randomUUID())
                .operationType("CLONE")
                .status("COMPLETED")
                .createdAt(Instant.now())
                .build();
        GitOperation op2 = GitOperation.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .taskId(taskId)
                .workspaceId(UUID.randomUUID())
                .operationType("PUSH")
                .status("FAILED")
                .errorMessage("auth error")
                .createdAt(Instant.now())
                .build();

        when(gitOperationService.listOperationsByTask(taskId)).thenReturn(List.of(op1, op2));

        mockMvc.perform(get("/api/git/operations/task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }
}
