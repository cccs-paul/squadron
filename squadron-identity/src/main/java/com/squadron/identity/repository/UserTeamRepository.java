package com.squadron.identity.repository;

import com.squadron.identity.entity.UserTeam;
import com.squadron.identity.entity.UserTeamId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserTeamRepository extends JpaRepository<UserTeam, UserTeamId> {

    List<UserTeam> findByTeamId(UUID teamId);

    List<UserTeam> findByUserId(UUID userId);
}
