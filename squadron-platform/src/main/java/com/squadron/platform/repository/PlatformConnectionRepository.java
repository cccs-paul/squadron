package com.squadron.platform.repository;

import com.squadron.platform.entity.PlatformConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlatformConnectionRepository extends JpaRepository<PlatformConnection, UUID> {

    List<PlatformConnection> findByTenantId(UUID tenantId);

    List<PlatformConnection> findByTenantIdAndPlatformType(UUID tenantId, String platformType);

    List<PlatformConnection> findByTenantIdAndStatus(UUID tenantId, String status);

    List<PlatformConnection> findByPlatformTypeAndStatus(String platformType, String status);

    List<PlatformConnection> findByPlatformTypeInAndStatus(List<String> platformTypes, String status);
}
