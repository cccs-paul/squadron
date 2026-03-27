package com.squadron.git.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.git.dto.BranchStrategyDto;
import com.squadron.git.dto.CreateBranchStrategyRequest;
import com.squadron.git.entity.BranchStrategy;
import com.squadron.git.repository.BranchStrategyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing branch naming strategies per tenant/project.
 * Supports hierarchical resolution: project-specific -> tenant default -> system default.
 */
@Service
@Transactional
public class BranchStrategyService {

    private static final Logger log = LoggerFactory.getLogger(BranchStrategyService.class);

    private final BranchStrategyRepository branchStrategyRepository;

    public BranchStrategyService(BranchStrategyRepository branchStrategyRepository) {
        this.branchStrategyRepository = branchStrategyRepository;
    }

    /**
     * Create a new branch strategy.
     */
    public BranchStrategyDto createStrategy(CreateBranchStrategyRequest request) {
        log.info("Creating branch strategy for tenant {} project {}", request.getTenantId(), request.getProjectId());

        BranchStrategy entity = BranchStrategy.builder()
                .tenantId(request.getTenantId())
                .projectId(request.getProjectId())
                .strategyType(request.getStrategyType())
                .branchPrefix(request.getBranchPrefix() != null ? request.getBranchPrefix() : "squadron/")
                .targetBranch(request.getTargetBranch())
                .developmentBranch(request.getDevelopmentBranch())
                .branchNameTemplate(request.getBranchNameTemplate() != null ? request.getBranchNameTemplate() : "{prefix}{taskId}/{slug}")
                .build();

        entity = branchStrategyRepository.save(entity);
        log.info("Branch strategy created with id {}", entity.getId());
        return toDto(entity);
    }

    /**
     * Get a branch strategy by ID.
     */
    @Transactional(readOnly = true)
    public BranchStrategyDto getStrategy(UUID id) {
        BranchStrategy entity = branchStrategyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BranchStrategy", id));
        return toDto(entity);
    }

    /**
     * Resolve the effective branch strategy for a tenant/project.
     * Resolution order: project-specific -> tenant default (projectId=null) -> system default.
     */
    @Transactional(readOnly = true)
    public BranchStrategyDto resolveStrategy(UUID tenantId, UUID projectId) {
        // 1. Try project-specific strategy
        if (projectId != null) {
            Optional<BranchStrategy> projectStrategy = branchStrategyRepository
                    .findByTenantIdAndProjectId(tenantId, projectId);
            if (projectStrategy.isPresent()) {
                log.debug("Resolved project-level branch strategy for tenant {} project {}", tenantId, projectId);
                return toDto(projectStrategy.get());
            }
        }

        // 2. Try tenant default (projectId is null)
        Optional<BranchStrategy> tenantDefault = branchStrategyRepository
                .findByTenantIdAndProjectIdIsNull(tenantId);
        if (tenantDefault.isPresent()) {
            log.debug("Resolved tenant-default branch strategy for tenant {}", tenantId);
            return toDto(tenantDefault.get());
        }

        // 3. Return system default
        log.debug("No branch strategy found for tenant {} project {}, returning system default", tenantId, projectId);
        return systemDefault(tenantId);
    }

    /**
     * List all branch strategies for a tenant.
     */
    @Transactional(readOnly = true)
    public List<BranchStrategyDto> listStrategies(UUID tenantId) {
        return branchStrategyRepository.findByTenantId(tenantId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Update an existing branch strategy.
     */
    public BranchStrategyDto updateStrategy(UUID id, CreateBranchStrategyRequest request) {
        BranchStrategy entity = branchStrategyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BranchStrategy", id));

        entity.setTenantId(request.getTenantId());
        entity.setProjectId(request.getProjectId());
        entity.setStrategyType(request.getStrategyType());
        entity.setTargetBranch(request.getTargetBranch());

        if (request.getBranchPrefix() != null) {
            entity.setBranchPrefix(request.getBranchPrefix());
        }
        if (request.getDevelopmentBranch() != null) {
            entity.setDevelopmentBranch(request.getDevelopmentBranch());
        }
        if (request.getBranchNameTemplate() != null) {
            entity.setBranchNameTemplate(request.getBranchNameTemplate());
        }

        entity = branchStrategyRepository.save(entity);
        log.info("Branch strategy {} updated", id);
        return toDto(entity);
    }

    /**
     * Delete a branch strategy.
     */
    public void deleteStrategy(UUID id) {
        if (!branchStrategyRepository.existsById(id)) {
            throw new ResourceNotFoundException("BranchStrategy", id);
        }
        branchStrategyRepository.deleteById(id);
        log.info("Branch strategy {} deleted", id);
    }

    /**
     * Generate a branch name using the resolved strategy for the given tenant/project.
     * Applies the template with substitutions: {prefix}, {taskId}, {slug}.
     * The slug is derived from the task title: lowercased, spaces to hyphens,
     * special characters stripped, truncated to 50 chars.
     */
    @Transactional(readOnly = true)
    public String generateBranchName(UUID tenantId, UUID projectId, UUID taskId, String taskTitle) {
        BranchStrategyDto strategy = resolveStrategy(tenantId, projectId);

        String slug = slugify(taskTitle);
        String shortTaskId = taskId.toString().substring(0, 8);

        String branchName = strategy.getBranchNameTemplate()
                .replace("{prefix}", strategy.getBranchPrefix() != null ? strategy.getBranchPrefix() : "")
                .replace("{taskId}", shortTaskId)
                .replace("{slug}", slug);

        log.debug("Generated branch name: {}", branchName);
        return branchName;
    }

    /**
     * Slugify a string: lowercase, replace spaces/underscores with hyphens,
     * strip non-alphanumeric characters (except hyphens), collapse multiple hyphens,
     * trim leading/trailing hyphens, truncate to 50 characters.
     */
    String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "unnamed";
        }

        String slug = input.toLowerCase()
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^a-z0-9\\-]", "")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");

        if (slug.isEmpty()) {
            return "unnamed";
        }

        if (slug.length() > 50) {
            slug = slug.substring(0, 50);
            // Don't end with a hyphen after truncation
            if (slug.endsWith("-")) {
                slug = slug.substring(0, slug.length() - 1);
            }
        }

        return slug;
    }

    private BranchStrategyDto systemDefault(UUID tenantId) {
        return BranchStrategyDto.builder()
                .tenantId(tenantId)
                .strategyType("TRUNK_BASED")
                .branchPrefix("squadron/")
                .targetBranch("main")
                .branchNameTemplate("{prefix}{taskId}/{slug}")
                .build();
    }

    private BranchStrategyDto toDto(BranchStrategy entity) {
        return BranchStrategyDto.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .projectId(entity.getProjectId())
                .strategyType(entity.getStrategyType())
                .branchPrefix(entity.getBranchPrefix())
                .targetBranch(entity.getTargetBranch())
                .developmentBranch(entity.getDevelopmentBranch())
                .branchNameTemplate(entity.getBranchNameTemplate())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
