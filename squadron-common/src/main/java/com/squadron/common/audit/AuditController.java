package com.squadron.common.audit;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller providing query access to audit events.
 * Tenant ID is always taken from TenantContext (set by TenantFilter from the gateway JWT).
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditEvent>>> getAuditEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Tenant context required"));
        }
        List<AuditEvent> events = auditQueryService.findByTenantId(tenantId, page, size);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<AuditEvent>>> getAuditEventsByUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Tenant context required"));
        }
        List<AuditEvent> events = auditQueryService.findByUserId(tenantId, userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/resource/{resourceType}/{resourceId}")
    public ResponseEntity<ApiResponse<List<AuditEvent>>> getAuditEventsByResource(
            @PathVariable String resourceType,
            @PathVariable String resourceId) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Tenant context required"));
        }
        List<AuditEvent> events = auditQueryService.findByResourceId(tenantId, resourceType, resourceId);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/daterange")
    public ResponseEntity<ApiResponse<List<AuditEvent>>> getAuditEventsByDateRange(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Tenant context required"));
        }
        List<AuditEvent> events = auditQueryService.findByDateRange(tenantId, from, to, page, size);
        return ResponseEntity.ok(ApiResponse.success(events));
    }
}
