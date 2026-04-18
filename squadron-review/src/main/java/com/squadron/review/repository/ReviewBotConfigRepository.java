package com.squadron.review.repository;

import com.squadron.review.entity.ReviewBotConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewBotConfigRepository extends JpaRepository<ReviewBotConfig, UUID> {

    Optional<ReviewBotConfig> findByTenantIdAndConnectionId(UUID tenantId, UUID connectionId);

    List<ReviewBotConfig> findByTenantId(UUID tenantId);

    Optional<ReviewBotConfig> findByTenantIdAndConnectionIdAndEnabledTrue(UUID tenantId, UUID connectionId);

    Optional<ReviewBotConfig> findByIdAndTenantId(UUID id, UUID tenantId);
}
