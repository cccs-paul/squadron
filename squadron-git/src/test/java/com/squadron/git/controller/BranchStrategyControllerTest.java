package com.squadron.git.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.git.dto.BranchStrategyDto;
import com.squadron.git.dto.CreateBranchStrategyRequest;
import com.squadron.git.service.BranchStrategyService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BranchStrategyController.class)
@AutoConfigureMockMvc(addFilters = false)
class BranchStrategyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BranchStrategyService branchStrategyService;

    @Test
    void should_createStrategy() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        CreateBranchStrategyRequest request = CreateBranchStrategyRequest.builder()
                .tenantId(tenantId)
                .strategyType("GITFLOW")
                .branchPrefix("feature/")
                .targetBranch("develop")
                .developmentBranch("develop")
                .build();

        BranchStrategyDto response = BranchStrategyDto.builder()
                .id(strategyId)
                .tenantId(tenantId)
                .strategyType("GITFLOW")
                .branchPrefix("feature/")
                .targetBranch("develop")
                .developmentBranch("develop")
                .branchNameTemplate("{prefix}{taskId}/{slug}")
                .createdAt(Instant.now())
                .build();

        when(branchStrategyService.createStrategy(any(CreateBranchStrategyRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/git/branch-strategies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(strategyId.toString()))
                .andExpect(jsonPath("$.data.strategyType").value("GITFLOW"))
                .andExpect(jsonPath("$.data.branchPrefix").value("feature/"))
                .andExpect(jsonPath("$.data.targetBranch").value("develop"));
    }

    @Test
    void should_resolveStrategy() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        BranchStrategyDto response = BranchStrategyDto.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .projectId(projectId)
                .strategyType("TRUNK_BASED")
                .branchPrefix("squadron/")
                .targetBranch("main")
                .branchNameTemplate("{prefix}{taskId}/{slug}")
                .createdAt(Instant.now())
                .build();

        when(branchStrategyService.resolveStrategy(tenantId, projectId)).thenReturn(response);

        mockMvc.perform(get("/api/git/branch-strategies/resolve")
                        .param("tenantId", tenantId.toString())
                        .param("projectId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.strategyType").value("TRUNK_BASED"))
                .andExpect(jsonPath("$.data.targetBranch").value("main"));
    }

    @Test
    void should_generateBranchName() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        when(branchStrategyService.generateBranchName(eq(tenantId), any(), eq(taskId), eq("Fix login bug")))
                .thenReturn("squadron/abcd1234/fix-login-bug");

        mockMvc.perform(get("/api/git/branch-strategies/generate-name")
                        .param("tenantId", tenantId.toString())
                        .param("taskId", taskId.toString())
                        .param("taskTitle", "Fix login bug"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("squadron/abcd1234/fix-login-bug"));
    }

    @Test
    void should_listStrategies() throws Exception {
        UUID tenantId = UUID.randomUUID();

        BranchStrategyDto s1 = BranchStrategyDto.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .strategyType("GITFLOW")
                .branchPrefix("feature/")
                .targetBranch("develop")
                .createdAt(Instant.now())
                .build();

        BranchStrategyDto s2 = BranchStrategyDto.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .strategyType("TRUNK_BASED")
                .branchPrefix("squadron/")
                .targetBranch("main")
                .createdAt(Instant.now())
                .build();

        when(branchStrategyService.listStrategies(tenantId)).thenReturn(List.of(s1, s2));

        mockMvc.perform(get("/api/git/branch-strategies")
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void should_getStrategy() throws Exception {
        UUID strategyId = UUID.randomUUID();

        BranchStrategyDto response = BranchStrategyDto.builder()
                .id(strategyId)
                .tenantId(UUID.randomUUID())
                .strategyType("CUSTOM")
                .branchPrefix("custom/")
                .targetBranch("release")
                .createdAt(Instant.now())
                .build();

        when(branchStrategyService.getStrategy(strategyId)).thenReturn(response);

        mockMvc.perform(get("/api/git/branch-strategies/{id}", strategyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(strategyId.toString()))
                .andExpect(jsonPath("$.data.strategyType").value("CUSTOM"));
    }

    @Test
    void should_updateStrategy() throws Exception {
        UUID strategyId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        CreateBranchStrategyRequest request = CreateBranchStrategyRequest.builder()
                .tenantId(tenantId)
                .strategyType("GITFLOW")
                .branchPrefix("feature/")
                .targetBranch("develop")
                .build();

        BranchStrategyDto response = BranchStrategyDto.builder()
                .id(strategyId)
                .tenantId(tenantId)
                .strategyType("GITFLOW")
                .branchPrefix("feature/")
                .targetBranch("develop")
                .createdAt(Instant.now())
                .build();

        when(branchStrategyService.updateStrategy(eq(strategyId), any(CreateBranchStrategyRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/git/branch-strategies/{id}", strategyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.strategyType").value("GITFLOW"))
                .andExpect(jsonPath("$.data.branchPrefix").value("feature/"));
    }

    @Test
    void should_deleteStrategy() throws Exception {
        UUID strategyId = UUID.randomUUID();

        doNothing().when(branchStrategyService).deleteStrategy(strategyId);

        mockMvc.perform(delete("/api/git/branch-strategies/{id}", strategyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(branchStrategyService).deleteStrategy(strategyId);
    }
}
