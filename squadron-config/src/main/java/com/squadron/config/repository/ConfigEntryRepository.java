package com.squadron.config.repository;

import com.squadron.config.entity.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConfigEntryRepository extends JpaRepository<ConfigEntry, UUID> {

    List<ConfigEntry> findByTenantIdAndConfigKey(UUID tenantId, String configKey);

    Optional<ConfigEntry> findByTenantIdAndTeamIdAndUserIdAndConfigKey(
            UUID tenantId, UUID teamId, UUID userId, String configKey);

    List<ConfigEntry> findByTenantIdAndTeamIdIsNullAndUserIdIsNull(UUID tenantId);

    List<ConfigEntry> findByTenantIdAndTeamIdAndUserIdIsNull(UUID tenantId, UUID teamId);

    List<ConfigEntry> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}
