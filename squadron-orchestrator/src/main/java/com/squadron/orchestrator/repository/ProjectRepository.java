package com.squadron.orchestrator.repository;

import com.squadron.orchestrator.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByTenantId(UUID tenantId);

    Page<Project> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Project> findByTenantIdAndNameContainingIgnoreCase(UUID tenantId, String name, Pageable pageable);

    List<Project> findByTeamId(UUID teamId);
}
