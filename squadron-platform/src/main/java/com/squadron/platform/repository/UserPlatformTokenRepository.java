package com.squadron.platform.repository;

import com.squadron.platform.entity.UserPlatformToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPlatformTokenRepository extends JpaRepository<UserPlatformToken, UUID> {

    Optional<UserPlatformToken> findByUserIdAndConnectionId(UUID userId, UUID connectionId);

    List<UserPlatformToken> findByUserId(UUID userId);
}
