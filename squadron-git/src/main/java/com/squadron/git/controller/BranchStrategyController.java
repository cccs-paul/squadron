package com.squadron.git.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.git.dto.BranchStrategyDto;
import com.squadron.git.dto.CreateBranchStrategyRequest;
import com.squadron.git.service.BranchStrategyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/git/branch-strategies")
public class BranchStrategyController {

    private final BranchStrategyService branchStrategyService;

    public BranchStrategyController(BranchStrategyService branchStrategyService) {
        this.branchStrategyService = branchStrategyService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BranchStrategyDto>> createStrategy(
            @Valid @RequestBody CreateBranchStrategyRequest request) {
        BranchStrategyDto dto = branchStrategyService.createStrategy(request);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BranchStrategyDto>> getStrategy(@PathVariable UUID id) {
        BranchStrategyDto dto = branchStrategyService.getStrategy(id);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/resolve")
    public ResponseEntity<ApiResponse<BranchStrategyDto>> resolveStrategy(
            @RequestParam UUID tenantId,
            @RequestParam(required = false) UUID projectId) {
        BranchStrategyDto dto = branchStrategyService.resolveStrategy(tenantId, projectId);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BranchStrategyDto>>> listStrategies(
            @RequestParam UUID tenantId) {
        List<BranchStrategyDto> dtos = branchStrategyService.listStrategies(tenantId);
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BranchStrategyDto>> updateStrategy(
            @PathVariable UUID id,
            @Valid @RequestBody CreateBranchStrategyRequest request) {
        BranchStrategyDto dto = branchStrategyService.updateStrategy(id, request);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStrategy(@PathVariable UUID id) {
        branchStrategyService.deleteStrategy(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/generate-name")
    public ResponseEntity<ApiResponse<String>> generateBranchName(
            @RequestParam UUID tenantId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam UUID taskId,
            @RequestParam String taskTitle) {
        String branchName = branchStrategyService.generateBranchName(tenantId, projectId, taskId, taskTitle);
        return ResponseEntity.ok(ApiResponse.success(branchName));
    }
}
