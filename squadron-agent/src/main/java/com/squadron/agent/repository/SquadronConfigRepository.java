package com.squadron.agent.repository;

import com.squadron.agent.entity.SquadronConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SquadronConfigRepository extends JpaRepository<SquadronConfig, UUID> {

    Optional<SquadronConfig> findByTenantIdAndTeamIdAndUserId(UUID tenantId, UUID teamId, UUID userId);

    Optional<SquadronConfig> findByTenantIdAndTeamIdAndUserIdIsNull(UUID tenantId, UUID teamId);

    Optional<SquadronConfig> findByTenantIdAndTeamIdIsNullAndUserIdIsNull(UUID tenantId);

    List<SquadronConfig> findByTenantId(UUID tenantId);
}
