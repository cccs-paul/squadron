package com.squadron.identity.repository;

import com.squadron.identity.entity.ResourcePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ResourcePermissionRepository extends JpaRepository<ResourcePermission, UUID> {

    List<ResourcePermission> findByTenantIdAndResourceTypeAndResourceId(UUID tenantId, String resourceType, UUID resourceId);

    List<ResourcePermission> findByGranteeTypeAndGranteeId(String granteeType, UUID granteeId);

    List<ResourcePermission> findByTenantIdAndGranteeTypeAndGranteeId(UUID tenantId, String granteeType, UUID granteeId);
}
