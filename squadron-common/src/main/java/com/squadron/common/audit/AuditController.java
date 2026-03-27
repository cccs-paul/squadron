package com.squadron.common.audit;

import com.squadron.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller providing query access to audit events.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public ApiResponse<List<AuditEvent>> getAuditEvents(
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<AuditEvent> events = auditQueryService.findByTenantId(tenantId, page, size);
        return ApiResponse.success(events);
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<AuditEvent>> getAuditEventsByUser(
            @PathVariable UUID userId,
            @RequestParam UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<AuditEvent> events = auditQueryService.findByUserId(tenantId, userId, page, size);
        return ApiResponse.success(events);
    }

    @GetMapping("/resource/{resourceType}/{resourceId}")
    public ApiResponse<List<AuditEvent>> getAuditEventsByResource(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            @RequestParam UUID tenantId) {
        List<AuditEvent> events = auditQueryService.findByResourceId(tenantId, resourceType, resourceId);
        return ApiResponse.success(events);
    }

    @GetMapping("/daterange")
    public ApiResponse<List<AuditEvent>> getAuditEventsByDateRange(
            @RequestParam UUID tenantId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<AuditEvent> events = auditQueryService.findByDateRange(tenantId, from, to, page, size);
        return ApiResponse.success(events);
    }
}
