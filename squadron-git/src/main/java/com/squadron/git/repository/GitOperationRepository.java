package com.squadron.git.repository;

import com.squadron.git.entity.GitOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GitOperationRepository extends JpaRepository<GitOperation, UUID> {

    List<GitOperation> findByTaskId(UUID taskId);

    List<GitOperation> findByWorkspaceId(UUID workspaceId);
}
