package com.squadron.platform.repository;

import com.squadron.platform.entity.SshKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SshKeyRepository extends JpaRepository<SshKey, UUID> {

    List<SshKey> findByTenantId(UUID tenantId);

    List<SshKey> findByConnectionId(UUID connectionId);

    List<SshKey> findByTenantIdAndConnectionId(UUID tenantId, UUID connectionId);

    boolean existsByTenantIdAndFingerprint(UUID tenantId, String fingerprint);
}
