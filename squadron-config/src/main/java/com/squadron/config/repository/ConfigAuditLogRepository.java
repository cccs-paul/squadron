package com.squadron.config.repository;

import com.squadron.config.entity.ConfigAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConfigAuditLogRepository extends JpaRepository<ConfigAuditLog, UUID> {

    List<ConfigAuditLog> findByConfigEntryIdOrderByChangedAtDesc(UUID configEntryId);

    List<ConfigAuditLog> findByTenantIdOrderByChangedAtDesc(UUID tenantId);
}
