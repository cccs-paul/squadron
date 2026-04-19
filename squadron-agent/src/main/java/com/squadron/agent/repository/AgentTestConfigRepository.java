package com.squadron.agent.repository;

import com.squadron.agent.entity.AgentTestConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for agent test configuration (test data generator model settings).
 */
public interface AgentTestConfigRepository extends JpaRepository<AgentTestConfig, UUID> {

    Optional<AgentTestConfig> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    void deleteByTenantIdAndUserId(UUID tenantId, UUID userId);
}
