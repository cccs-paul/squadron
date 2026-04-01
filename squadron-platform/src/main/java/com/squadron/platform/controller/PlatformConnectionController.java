package com.squadron.platform.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.platform.dto.ConnectionInfoResponse;
import com.squadron.platform.dto.CreateConnectionRequest;
import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.service.PlatformConnectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/platforms/connections")
public class PlatformConnectionController {

    private final PlatformConnectionService connectionService;

    public PlatformConnectionController(PlatformConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConnectionInfoResponse>> createConnection(
            @Valid @RequestBody CreateConnectionRequest request) {
        PlatformConnection connection = connectionService.createConnection(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ConnectionInfoResponse.fromEntity(connection)));
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<ApiResponse<List<ConnectionInfoResponse>>> listByTenant(
            @PathVariable UUID tenantId) {
        List<PlatformConnection> connections = connectionService.listConnectionsByTenant(tenantId);
        List<ConnectionInfoResponse> response = connections.stream()
                .map(ConnectionInfoResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ConnectionInfoResponse>> getConnection(@PathVariable UUID id) {
        PlatformConnection connection = connectionService.getConnection(id);
        return ResponseEntity.ok(ApiResponse.success(ConnectionInfoResponse.fromEntity(connection)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ConnectionInfoResponse>> updateConnection(
            @PathVariable UUID id,
            @Valid @RequestBody CreateConnectionRequest request) {
        PlatformConnection connection = connectionService.updateConnection(id, request);
        return ResponseEntity.ok(ApiResponse.success(ConnectionInfoResponse.fromEntity(connection)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConnection(@PathVariable UUID id) {
        connectionService.deleteConnection(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<ApiResponse<Boolean>> testConnection(@PathVariable UUID id) {
        boolean result = connectionService.testConnection(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Fetches available workflow statuses from the remote ticketing platform
     * for a given connection and project key.
     */
    @GetMapping("/{id}/statuses")
    public ResponseEntity<ApiResponse<List<String>>> getProjectStatuses(
            @PathVariable UUID id,
            @RequestParam String projectKey) {
        List<String> statuses = connectionService.fetchProjectStatuses(id, projectKey);
        return ResponseEntity.ok(ApiResponse.success(statuses));
    }
}
