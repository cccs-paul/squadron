package com.squadron.review.repository;

import com.squadron.review.entity.QAReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QAReportRepository extends JpaRepository<QAReport, UUID> {

    List<QAReport> findByTaskId(UUID taskId);

    Optional<QAReport> findFirstByTaskIdOrderByCreatedAtDesc(UUID taskId);

    List<QAReport> findByTenantId(UUID tenantId);

    long countByTaskIdAndVerdict(UUID taskId, String verdict);
}
