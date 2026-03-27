package com.squadron.identity.repository;

import com.squadron.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByExternalId(String externalId);

    Optional<User> findByExternalIdAndTenantId(String externalId, UUID tenantId);

    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

    Optional<User> findByEmail(String email);

    List<User> findByTenantId(UUID tenantId);

    List<User> findByTenantIdAndRole(UUID tenantId, String role);
}
