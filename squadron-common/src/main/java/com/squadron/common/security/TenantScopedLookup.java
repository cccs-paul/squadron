package com.squadron.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utility for tenant-scoped entity lookups. Uses TenantContext to automatically
 * scope findById calls to the current tenant, preventing cross-tenant data access.
 */
public final class TenantScopedLookup {

    private static final Logger log = LoggerFactory.getLogger(TenantScopedLookup.class);

    private TenantScopedLookup() {}

    /**
     * Finds an entity by ID, scoped to the current tenant from TenantContext.
     * If no tenant context is available (e.g., NATS listeners, scheduled tasks),
     * falls back to an unscoped lookup with a warning log.
     *
     * @param id                    the entity ID
     * @param findById              unscoped lookup function (fallback)
     * @param findByIdAndTenantId   tenant-scoped lookup function
     * @param notFoundSupplier      exception supplier when entity is not found
     * @param <T>                   the entity type
     * @return the found entity
     * @throws RuntimeException from notFoundSupplier if entity is not found
     */
    public static <T> T findByIdScoped(
            UUID id,
            Function<UUID, Optional<T>> findById,
            BiFunction<UUID, UUID, Optional<T>> findByIdAndTenantId,
            Supplier<RuntimeException> notFoundSupplier) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return findByIdAndTenantId.apply(id, tenantId)
                    .orElseThrow(notFoundSupplier);
        }
        log.warn("No tenant context for scoped lookup of entity {}", id);
        return findById.apply(id).orElseThrow(notFoundSupplier);
    }
}
