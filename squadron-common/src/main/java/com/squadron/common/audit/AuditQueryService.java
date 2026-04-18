package com.squadron.common.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory audit event query service backed by a bounded concurrent deque.
 * <p>
 * Uses a ConcurrentLinkedDeque with an AtomicInteger size counter instead of
 * CopyOnWriteArrayList for better write performance under contention.
 */
public class AuditQueryService {

    static final int MAX_BUFFER_SIZE = 10_000;

    private final ConcurrentLinkedDeque<AuditEvent> buffer = new ConcurrentLinkedDeque<>();
    private final AtomicInteger size = new AtomicInteger(0);

    /**
     * Stores an audit event in the buffer, evicting the oldest event if the
     * buffer is at maximum capacity.
     */
    public void store(AuditEvent event) {
        if (event == null) {
            return;
        }
        buffer.addLast(event);
        int currentSize = size.incrementAndGet();
        // Evict oldest entries if over capacity
        while (currentSize > MAX_BUFFER_SIZE) {
            AuditEvent evicted = buffer.pollFirst();
            if (evicted != null) {
                currentSize = size.decrementAndGet();
            } else {
                break;
            }
        }
    }

    public List<AuditEvent> findByTenantId(UUID tenantId, int page, int size) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        List<AuditEvent> filtered = buffer.stream()
                .filter(e -> tenantId.equals(e.getTenantId()))
                .toList();
        return paginate(filtered, page, size);
    }

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

    public int size() {
        return size.get();
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
