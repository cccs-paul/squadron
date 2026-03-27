package com.squadron.common.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory audit event query service backed by a bounded concurrent buffer.
 * <p>
 * This implementation stores audit events in an in-memory list with a maximum
 * capacity of {@value #MAX_BUFFER_SIZE}. When the buffer is full, the oldest
 * events are evicted.
 * <p>
 * In a production environment, this would be replaced by a database-backed
 * implementation.
 */
public class AuditQueryService {

    static final int MAX_BUFFER_SIZE = 10_000;

    private final CopyOnWriteArrayList<AuditEvent> buffer = new CopyOnWriteArrayList<>();

    /**
     * Stores an audit event in the buffer, evicting the oldest event if the
     * buffer is at maximum capacity.
     */
    public void store(AuditEvent event) {
        if (event == null) {
            return;
        }
        // Evict oldest if at capacity
        while (buffer.size() >= MAX_BUFFER_SIZE) {
            buffer.remove(0);
        }
        buffer.add(event);
    }

    /**
     * Find audit events by tenant ID with pagination.
     */
    public List<AuditEvent> findByTenantId(UUID tenantId, int page, int size) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        List<AuditEvent> filtered = buffer.stream()
                .filter(e -> tenantId.equals(e.getTenantId()))
                .toList();
        return paginate(filtered, page, size);
    }

    /**
     * Find audit events by user ID within a tenant, with pagination.
     */
    public List<AuditEvent> findByUserId(UUID tenantId, UUID userId, int page, int size) {
        if (tenantId == null || userId == null) {
            return Collections.emptyList();
        }
        List<AuditEvent> filtered = buffer.stream()
                .filter(e -> tenantId.equals(e.getTenantId()))
                .filter(e -> userId.equals(e.getUserId()))
                .toList();
        return paginate(filtered, page, size);
    }

    /**
     * Find audit events by resource type within a tenant, with pagination.
     */
    public List<AuditEvent> findByResourceType(UUID tenantId, String resourceType, int page, int size) {
        if (tenantId == null || resourceType == null) {
            return Collections.emptyList();
        }
        List<AuditEvent> filtered = buffer.stream()
                .filter(e -> tenantId.equals(e.getTenantId()))
                .filter(e -> resourceType.equals(e.getResourceType()))
                .toList();
        return paginate(filtered, page, size);
    }

    /**
     * Find audit events by resource type and resource ID within a tenant.
     */
    public List<AuditEvent> findByResourceId(UUID tenantId, String resourceType, String resourceId) {
        if (tenantId == null || resourceType == null || resourceId == null) {
            return Collections.emptyList();
        }
        return buffer.stream()
                .filter(e -> tenantId.equals(e.getTenantId()))
                .filter(e -> resourceType.equals(e.getResourceType()))
                .filter(e -> resourceId.equals(e.getResourceId()))
                .toList();
    }

    /**
     * Find audit events within a date range for a tenant, with pagination.
     */
    public List<AuditEvent> findByDateRange(UUID tenantId, Instant from, Instant to, int page, int size) {
        if (tenantId == null || from == null || to == null) {
            return Collections.emptyList();
        }
        List<AuditEvent> filtered = buffer.stream()
                .filter(e -> tenantId.equals(e.getTenantId()))
                .filter(e -> e.getTimestamp() != null)
                .filter(e -> !e.getTimestamp().isBefore(from) && !e.getTimestamp().isAfter(to))
                .toList();
        return paginate(filtered, page, size);
    }

    /**
     * Returns the current number of events in the buffer.
     */
    public int size() {
        return buffer.size();
    }

    private List<AuditEvent> paginate(List<AuditEvent> list, int page, int size) {
        if (size <= 0) {
            size = 50;
        }
        if (page < 0) {
            page = 0;
        }
        int start = page * size;
        if (start >= list.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(start + size, list.size());
        return new ArrayList<>(list.subList(start, end));
    }
}
