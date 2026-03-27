package com.squadron.identity.repository;

import com.squadron.identity.entity.SecurityGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecurityGroupRepository extends JpaRepository<SecurityGroup, UUID> {

    List<SecurityGroup> findByTenantId(UUID tenantId);

    Optional<SecurityGroup> findByTenantIdAndName(UUID tenantId, String name);
}
