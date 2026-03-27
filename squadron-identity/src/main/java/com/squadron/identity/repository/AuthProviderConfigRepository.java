package com.squadron.identity.repository;

import com.squadron.identity.entity.AuthProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuthProviderConfigRepository extends JpaRepository<AuthProviderConfig, UUID> {

    List<AuthProviderConfig> findByTenantId(UUID tenantId);

    List<AuthProviderConfig> findByTenantIdAndProviderType(UUID tenantId, String providerType);

    List<AuthProviderConfig> findByTenantIdAndEnabled(UUID tenantId, boolean enabled);

    List<AuthProviderConfig> findByTenantIdAndEnabledOrderByPriorityAsc(UUID tenantId, boolean enabled);
}
