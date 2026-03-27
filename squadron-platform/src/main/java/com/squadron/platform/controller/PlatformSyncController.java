package com.squadron.platform.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.platform.dto.PlatformTaskDto;
import com.squadron.platform.service.PlatformSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/platforms/sync")
public class PlatformSyncController {

    private final PlatformSyncService syncService;

    public PlatformSyncController(PlatformSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/{connectionId}/tasks")
    public ResponseEntity<ApiResponse<List<PlatformTaskDto>>> syncTasks(
            @PathVariable UUID connectionId,
            @RequestParam String projectKey) {
        List<PlatformTaskDto> tasks = syncService.syncTasks(connectionId, projectKey);
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @PostMapping("/{connectionId}/push-status")
    public ResponseEntity<ApiResponse<Void>> pushStatus(
            @PathVariable UUID connectionId,
            @RequestParam String externalId,
            @RequestParam String status,
            @RequestParam(required = false) String comment,
            @RequestParam(required = false) UUID userId) {
        syncService.pushTaskStatus(connectionId, externalId, status, comment, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
