package com.squadron.agent.repository;

import com.squadron.agent.entity.UserAgentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAgentConfigRepository extends JpaRepository<UserAgentConfig, UUID> {

    /**
     * Find all agent configs for a user, ordered by display order.
     */
    List<UserAgentConfig> findByTenantIdAndUserIdOrderByDisplayOrderAsc(UUID tenantId, UUID userId);

    /**
     * Count how many agents a user currently has.
     */
    long countByTenantIdAndUserId(UUID tenantId, UUID userId);

    /**
     * Find a specific agent by name for a user (names are unique per user).
     */
    Optional<UserAgentConfig> findByTenantIdAndUserIdAndAgentName(UUID tenantId, UUID userId, String agentName);

    /**
     * Check if a user already has an agent with a given name.
     */
    boolean existsByTenantIdAndUserIdAndAgentName(UUID tenantId, UUID userId, String agentName);

    /**
     * Delete all agents for a user (used during reset).
     */
    void deleteByTenantIdAndUserId(UUID tenantId, UUID userId);
}
