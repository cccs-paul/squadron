package com.squadron.git.repository;

import com.squadron.git.entity.PullRequestRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PullRequestRecordRepository extends JpaRepository<PullRequestRecord, UUID> {

    Optional<PullRequestRecord> findByTaskId(UUID taskId);

    List<PullRequestRecord> findByTenantIdAndStatus(UUID tenantId, String status);
}
