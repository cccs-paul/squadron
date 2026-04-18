package com.squadron.identity.repository;

import com.squadron.identity.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findByTenantId(UUID tenantId);

    Optional<Team> findByIdAndTenantId(UUID id, UUID tenantId);
}
