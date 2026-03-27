package com.squadron.workspace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.workspace.dto.CreateWorkspaceRequest;
import com.squadron.workspace.dto.ExecRequest;
import com.squadron.workspace.dto.ExecResult;
import com.squadron.workspace.dto.WorkspaceDto;
import com.squadron.workspace.service.WorkspaceGitService;
import com.squadron.workspace.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ContextConfiguration(classes = {WorkspaceController.class})
@AutoConfigureMockMvc(addFilters = false)
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WorkspaceService workspaceService;

    @MockBean
    private WorkspaceGitService workspaceGitService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void should_createWorkspace() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        CreateWorkspaceRequest request = CreateWorkspaceRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .repoUrl("https://github.com/test/repo.git")
                .branch("main")
                .build();

        WorkspaceDto response = WorkspaceDto.builder()
                .id(workspaceId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .providerType("KUBERNETES")
                .containerId("pod-abc123")
                .status("READY")
                .repoUrl("https://github.com/test/repo.git")
                .branch("main")
                .createdAt(Instant.now())
                .build();

        when(workspaceService.createWorkspace(any(CreateWorkspaceRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(workspaceId.toString()))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.containerId").value("pod-abc123"));
    }

    @Test
    void should_getWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        WorkspaceDto response = WorkspaceDto.builder()
                .id(workspaceId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .status("READY")
                .repoUrl("https://github.com/test/repo.git")
                .createdAt(Instant.now())
                .build();

        when(workspaceService.getWorkspace(workspaceId)).thenReturn(response);

        mockMvc.perform(get("/api/workspaces/{id}", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(workspaceId.toString()));
    }

    @Test
    void should_listByTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        WorkspaceDto w1 = WorkspaceDto.builder().id(UUID.randomUUID()).taskId(taskId).status("READY")
                .repoUrl("https://github.com/test/repo.git").createdAt(Instant.now()).build();
        WorkspaceDto w2 = WorkspaceDto.builder().id(UUID.randomUUID()).taskId(taskId).status("TERMINATED")
                .repoUrl("https://github.com/test/repo.git").createdAt(Instant.now()).build();

        when(workspaceService.listWorkspacesByTask(taskId)).thenReturn(List.of(w1, w2));

        mockMvc.perform(get("/api/workspaces/task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void should_destroyWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        doNothing().when(workspaceService).destroyWorkspace(workspaceId);

        mockMvc.perform(delete("/api/workspaces/{id}", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(workspaceService).destroyWorkspace(workspaceId);
    }

    @Test
    void should_execInWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        ExecRequest request = ExecRequest.builder()
                .workspaceId(workspaceId)
                .command(List.of("ls", "-la"))
                .build();

        ExecResult execResult = ExecResult.builder()
                .exitCode(0)
                .stdout("total 0")
                .stderr("")
                .durationMs(50)
                .build();

        when(workspaceService.execInWorkspace(any(ExecRequest.class))).thenReturn(execResult);

        mockMvc.perform(post("/api/workspaces/{id}/exec", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exitCode").value(0))
                .andExpect(jsonPath("$.data.stdout").value("total 0"));
    }

    @Test
    void should_listActiveWorkspaces() throws Exception {
        UUID tenantId = UUID.randomUUID();
        WorkspaceDto w1 = WorkspaceDto.builder().id(UUID.randomUUID()).tenantId(tenantId).status("READY")
                .repoUrl("https://github.com/test/repo.git").createdAt(Instant.now()).build();

        when(workspaceService.listActiveWorkspaces(tenantId)).thenReturn(List.of(w1));

        mockMvc.perform(get("/api/workspaces/active/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void should_returnBadRequest_whenMissingRequiredFields() throws Exception {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest();

        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_overrideWorkspaceIdFromPath() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        ExecRequest request = ExecRequest.builder()
                .workspaceId(UUID.randomUUID())
                .command(List.of("echo", "hello"))
                .build();

        ExecResult execResult = ExecResult.builder()
                .exitCode(0)
                .stdout("hello")
                .stderr("")
                .durationMs(10)
                .build();

        when(workspaceService.execInWorkspace(any(ExecRequest.class))).thenReturn(execResult);

        mockMvc.perform(post("/api/workspaces/{id}/exec", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(workspaceService).execInWorkspace(any(ExecRequest.class));
    }

    @Test
    void should_copyToWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        byte[] content = "file content".getBytes();

        doNothing().when(workspaceService).copyToWorkspace(eq(workspaceId), any(byte[].class), eq("/tmp/test.txt"));

        mockMvc.perform(post("/api/workspaces/{id}/copy-to", workspaceId)
                        .param("path", "/tmp/test.txt")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(workspaceService).copyToWorkspace(eq(workspaceId), any(byte[].class), eq("/tmp/test.txt"));
    }

    @Test
    void should_copyFromWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        byte[] content = "file content".getBytes();

        when(workspaceService.copyFromWorkspace(workspaceId, "/tmp/test.txt")).thenReturn(content);

        mockMvc.perform(get("/api/workspaces/{id}/copy-from", workspaceId)
                        .param("path", "/tmp/test.txt"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(content));

        verify(workspaceService).copyFromWorkspace(workspaceId, "/tmp/test.txt");
    }

    @Test
    void should_cloneRepo() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        ExecResult execResult = ExecResult.builder()
                .exitCode(0).stdout("").stderr("").durationMs(5000).build();

        when(workspaceGitService.cloneRepository(eq(workspaceId), eq("my-token"))).thenReturn(execResult);

        mockMvc.perform(post("/api/workspaces/{id}/git/clone", workspaceId)
                        .param("accessToken", "my-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exitCode").value(0));

        verify(workspaceGitService).cloneRepository(workspaceId, "my-token");
    }

    @Test
    void should_createBranch() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        ExecResult execResult = ExecResult.builder()
                .exitCode(0).stdout("Switched to a new branch 'feature/new'").stderr("").durationMs(50).build();

        when(workspaceGitService.createBranch(eq(workspaceId), eq("feature/new"), eq("main")))
                .thenReturn(execResult);

        mockMvc.perform(post("/api/workspaces/{id}/git/branch", workspaceId)
                        .param("branchName", "feature/new")
                        .param("baseBranch", "main"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exitCode").value(0));

        verify(workspaceGitService).createBranch(workspaceId, "feature/new", "main");
    }

    @Test
    void should_commitChanges() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        ExecResult execResult = ExecResult.builder()
                .exitCode(0).stdout("[main abc1234] Fix bug").stderr("").durationMs(100).build();

        when(workspaceGitService.commitChanges(eq(workspaceId), eq("Fix bug"), eq("User"), eq("user@test.com")))
                .thenReturn(execResult);

        mockMvc.perform(post("/api/workspaces/{id}/git/commit", workspaceId)
                        .param("message", "Fix bug")
                        .param("authorName", "User")
                        .param("authorEmail", "user@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exitCode").value(0));

        verify(workspaceGitService).commitChanges(workspaceId, "Fix bug", "User", "user@test.com");
    }

    @Test
    void should_pushChanges() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        ExecResult execResult = ExecResult.builder()
                .exitCode(0).stdout("").stderr("").durationMs(3000).build();

        when(workspaceGitService.pushChanges(eq(workspaceId), eq("main"), eq("my-token")))
                .thenReturn(execResult);

        mockMvc.perform(post("/api/workspaces/{id}/git/push", workspaceId)
                        .param("branch", "main")
                        .param("accessToken", "my-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exitCode").value(0));

        verify(workspaceGitService).pushChanges(workspaceId, "main", "my-token");
    }

    @Test
    void should_getDiff() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        ExecResult execResult = ExecResult.builder()
                .exitCode(0).stdout("diff --git a/file.txt b/file.txt").stderr("").durationMs(50).build();

        when(workspaceGitService.getDiff(workspaceId)).thenReturn(execResult);

        mockMvc.perform(get("/api/workspaces/{id}/git/diff", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exitCode").value(0))
                .andExpect(jsonPath("$.data.stdout").value("diff --git a/file.txt b/file.txt"));

        verify(workspaceGitService).getDiff(workspaceId);
    }

    @Test
    void should_getGitStatus() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        ExecResult execResult = ExecResult.builder()
                .exitCode(0).stdout("On branch main\nnothing to commit").stderr("").durationMs(50).build();

        when(workspaceGitService.getStatus(workspaceId)).thenReturn(execResult);

        mockMvc.perform(get("/api/workspaces/{id}/git/status", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exitCode").value(0));

        verify(workspaceGitService).getStatus(workspaceId);
    }
}
