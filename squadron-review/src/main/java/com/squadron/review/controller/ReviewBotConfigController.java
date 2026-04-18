package com.squadron.review.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.review.dto.CreateReviewBotConfigRequest;
import com.squadron.review.dto.ReviewBotConfigDto;
import com.squadron.review.service.ReviewBotConfigService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews/bot-config")
public class ReviewBotConfigController {

    private final ReviewBotConfigService botConfigService;

    public ReviewBotConfigController(ReviewBotConfigService botConfigService) {
        this.botConfigService = botConfigService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('squadron-admin', 'team-lead', 'developer')")
    public ResponseEntity<ApiResponse<ReviewBotConfigDto>> createBotConfig(
            @Valid @RequestBody CreateReviewBotConfigRequest request) {
        ReviewBotConfigDto dto = botConfigService.createBotConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin', 'team-lead', 'developer')")
    public ResponseEntity<ApiResponse<ReviewBotConfigDto>> getBotConfig(@PathVariable UUID id) {
        ReviewBotConfigDto dto = botConfigService.getBotConfig(id);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasAnyRole('squadron-admin', 'team-lead', 'developer')")
    public ResponseEntity<ApiResponse<List<ReviewBotConfigDto>>> listBotConfigs(@PathVariable UUID tenantId) {
        List<ReviewBotConfigDto> configs = botConfigService.listBotConfigsByTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin', 'team-lead', 'developer')")
    public ResponseEntity<ApiResponse<ReviewBotConfigDto>> updateBotConfig(
            @PathVariable UUID id, @Valid @RequestBody CreateReviewBotConfigRequest request) {
        ReviewBotConfigDto dto = botConfigService.updateBotConfig(id, request);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin', 'team-lead', 'developer')")
    public ResponseEntity<ApiResponse<Void>> deleteBotConfig(@PathVariable UUID id) {
        botConfigService.deleteBotConfig(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/token")
    @PreAuthorize("hasRole('squadron-admin')")
    public ResponseEntity<ApiResponse<String>> getBotToken(@PathVariable UUID id) {
        ReviewBotConfigDto config = botConfigService.getBotConfig(id);
        UUID callerTenantId = com.squadron.common.security.TenantContext.getTenantId();
        if (callerTenantId != null && !callerTenantId.equals(config.getTenantId())) {
            return ResponseEntity.status(403).body(ApiResponse.error("Tenant mismatch"));
        }
        String token = botConfigService.getDecryptedBotToken(id);
        return ResponseEntity.ok(ApiResponse.success(token));
    }
}
