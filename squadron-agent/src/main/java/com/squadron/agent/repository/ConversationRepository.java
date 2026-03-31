package com.squadron.agent.repository;

import com.squadron.agent.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByTaskIdAndAgentType(UUID taskId, String agentType);

    List<Conversation> findByTaskId(UUID taskId);

    List<Conversation> findByUserIdAndStatus(UUID userId, String status);

    Optional<Conversation> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Conversation> findByTenantIdAndStatus(UUID tenantId, String status);

    List<Conversation> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
