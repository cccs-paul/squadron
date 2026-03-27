package com.squadron.agent.repository;

import com.squadron.agent.entity.TokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TokenUsageRepository extends JpaRepository<TokenUsage, UUID> {

    List<TokenUsage> findByTenantId(UUID tenantId);

    List<TokenUsage> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<TokenUsage> findByTenantIdAndTeamId(UUID tenantId, UUID teamId);

    List<TokenUsage> findByTenantIdAndAgentType(UUID tenantId, String agentType);

    List<TokenUsage> findByTenantIdAndCreatedAtBetween(UUID tenantId, Instant start, Instant end);

    List<TokenUsage> findByTenantIdAndUserIdAndCreatedAtBetween(UUID tenantId, UUID userId, Instant start, Instant end);
}
