package com.squadron.git.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.git.dto.BranchStrategyDto;
import com.squadron.git.dto.CreateBranchStrategyRequest;
import com.squadron.git.entity.BranchStrategy;
import com.squadron.git.repository.BranchStrategyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchStrategyServiceTest {

    @Mock
    private BranchStrategyRepository branchStrategyRepository;

    private BranchStrategyService branchStrategyService;

    @BeforeEach
    void setUp() {
        branchStrategyService = new BranchStrategyService(branchStrategyRepository);
    }

    @Test
    void should_createStrategy() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        CreateBranchStrategyRequest request = CreateBranchStrategyRequest.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .strategyType("GITFLOW")
                .branchPrefix("feature/")
                .targetBranch("develop")
                .developmentBranch("develop")
                .branchNameTemplate("{prefix}{taskId}/{slug}")
                .build();

        when(branchStrategyRepository.save(any(BranchStrategy.class))).thenAnswer(invocation -> {
            BranchStrategy entity = invocation.getArgument(0);
            entity.setId(strategyId);
            entity.setCreatedAt(Instant.now());
            return entity;
        });

        BranchStrategyDto result = branchStrategyService.createStrategy(request);

        assertNotNull(result);
        assertEquals(strategyId, result.getId());
        assertEquals(tenantId, result.getTenantId());
        assertEquals(projectId, result.getProjectId());
        assertEquals("GITFLOW", result.getStrategyType());
        assertEquals("feature/", result.getBranchPrefix());
        assertEquals("develop", result.getTargetBranch());
        assertEquals("develop", result.getDevelopmentBranch());

        verify(branchStrategyRepository).save(any(BranchStrategy.class));
    }

    @Test
    void should_resolveStrategy_projectLevel() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        BranchStrategy projectStrategy = BranchStrategy.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .projectId(projectId)
                .strategyType("GITFLOW")
                .branchPrefix("feature/")
                .targetBranch("develop")
                .branchNameTemplate("{prefix}{taskId}/{slug}")
                .createdAt(Instant.now())
                .build();

        when(branchStrategyRepository.findByTenantIdAndProjectId(tenantId, projectId))
                .thenReturn(Optional.of(projectStrategy));

        BranchStrategyDto result = branchStrategyService.resolveStrategy(tenantId, projectId);

        assertNotNull(result);
        assertEquals("GITFLOW", result.getStrategyType());
        assertEquals("feature/", result.getBranchPrefix());
        assertEquals("develop", result.getTargetBranch());

        verify(branchStrategyRepository).findByTenantIdAndProjectId(tenantId, projectId);
        verify(branchStrategyRepository, never()).findByTenantIdAndProjectIdIsNull(any());
    }

    @Test
    void should_resolveStrategy_tenantDefault_when_noProjectStrategy() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        BranchStrategy tenantDefault = BranchStrategy.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .strategyType("TRUNK_BASED")
                .branchPrefix("squadron/")
                .targetBranch("main")
                .branchNameTemplate("{prefix}{taskId}/{slug}")
                .createdAt(Instant.now())
                .build();

        when(branchStrategyRepository.findByTenantIdAndProjectId(tenantId, projectId))
                .thenReturn(Optional.empty());
        when(branchStrategyRepository.findByTenantIdAndProjectIdIsNull(tenantId))
                .thenReturn(Optional.of(tenantDefault));

        BranchStrategyDto result = branchStrategyService.resolveStrategy(tenantId, projectId);

        assertNotNull(result);
        assertEquals("TRUNK_BASED", result.getStrategyType());
        assertEquals("squadron/", result.getBranchPrefix());
        assertEquals("main", result.getTargetBranch());
    }

    @Test
    void should_resolveStrategy_systemDefault_when_noStrategy() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(branchStrategyRepository.findByTenantIdAndProjectId(tenantId, projectId))
                .thenReturn(Optional.empty());
        when(branchStrategyRepository.findByTenantIdAndProjectIdIsNull(tenantId))
                .thenReturn(Optional.empty());

        BranchStrategyDto result = branchStrategyService.resolveStrategy(tenantId, projectId);

        assertNotNull(result);
        assertEquals("TRUNK_BASED", result.getStrategyType());
        assertEquals("squadron/", result.getBranchPrefix());
        assertEquals("main", result.getTargetBranch());
        assertEquals("{prefix}{taskId}/{slug}", result.getBranchNameTemplate());
        assertNull(result.getId()); // system default has no persisted ID
    }

    @Test
    void should_resolveStrategy_systemDefault_when_projectIdIsNull() {
        UUID tenantId = UUID.randomUUID();

        when(branchStrategyRepository.findByTenantIdAndProjectIdIsNull(tenantId))
                .thenReturn(Optional.empty());

        BranchStrategyDto result = branchStrategyService.resolveStrategy(tenantId, null);

        assertNotNull(result);
        assertEquals("TRUNK_BASED", result.getStrategyType());

        // Should NOT call findByTenantIdAndProjectId when projectId is null
        verify(branchStrategyRepository, never()).findByTenantIdAndProjectId(any(), any());
    }

    @Test
    void should_generateBranchName() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        String shortTaskId = taskId.toString().substring(0, 8);

        when(branchStrategyRepository.findByTenantIdAndProjectIdIsNull(tenantId))
                .thenReturn(Optional.empty());

        String branchName = branchStrategyService.generateBranchName(tenantId, null, taskId, "Fix login bug");

        assertNotNull(branchName);
        assertTrue(branchName.startsWith("squadron/" + shortTaskId + "/"));
        assertTrue(branchName.contains("fix-login-bug"));
    }

    @Test
    void should_generateBranchName_withSlugification() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        when(branchStrategyRepository.findByTenantIdAndProjectIdIsNull(tenantId))
                .thenReturn(Optional.empty());

        String branchName = branchStrategyService.generateBranchName(
                tenantId, null, taskId, "Add OAuth2 Login!!! @#$% Support");

        assertNotNull(branchName);
        // Special chars should be stripped, spaces become hyphens
        assertTrue(branchName.contains("add-oauth2-login-support"));
    }

    @Test
    void should_listStrategies() {
        UUID tenantId = UUID.randomUUID();

        BranchStrategy s1 = BranchStrategy.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .strategyType("GITFLOW")
                .branchPrefix("feature/")
                .targetBranch("develop")
                .branchNameTemplate("{prefix}{taskId}/{slug}")
                .createdAt(Instant.now())
                .build();

        BranchStrategy s2 = BranchStrategy.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .projectId(UUID.randomUUID())
                .strategyType("TRUNK_BASED")
                .branchPrefix("squadron/")
                .targetBranch("main")
                .branchNameTemplate("{prefix}{taskId}/{slug}")
                .createdAt(Instant.now())
                .build();

        when(branchStrategyRepository.findByTenantId(tenantId)).thenReturn(List.of(s1, s2));

        List<BranchStrategyDto> results = branchStrategyService.listStrategies(tenantId);

        assertEquals(2, results.size());
    }

    @Test
    void should_updateStrategy() {
        UUID strategyId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        BranchStrategy existing = BranchStrategy.builder()
                .id(strategyId)
                .tenantId(tenantId)
                .strategyType("TRUNK_BASED")
                .branchPrefix("squadron/")
                .targetBranch("main")
                .branchNameTemplate("{prefix}{taskId}/{slug}")
                .createdAt(Instant.now())
                .build();

        CreateBranchStrategyRequest updateRequest = CreateBranchStrategyRequest.builder()
                .tenantId(tenantId)
                .strategyType("GITFLOW")
                .branchPrefix("feature/")
                .targetBranch("develop")
                .developmentBranch("develop")
                .branchNameTemplate("{prefix}{slug}")
                .build();

        when(branchStrategyRepository.findById(strategyId)).thenReturn(Optional.of(existing));
        when(branchStrategyRepository.save(any(BranchStrategy.class))).thenAnswer(i -> i.getArgument(0));

        BranchStrategyDto result = branchStrategyService.updateStrategy(strategyId, updateRequest);

        assertNotNull(result);
        assertEquals("GITFLOW", result.getStrategyType());
        assertEquals("feature/", result.getBranchPrefix());
        assertEquals("develop", result.getTargetBranch());
        assertEquals("develop", result.getDevelopmentBranch());
        assertEquals("{prefix}{slug}", result.getBranchNameTemplate());
    }

    @Test
    void should_deleteStrategy() {
        UUID strategyId = UUID.randomUUID();

        when(branchStrategyRepository.existsById(strategyId)).thenReturn(true);
        doNothing().when(branchStrategyRepository).deleteById(strategyId);

        assertDoesNotThrow(() -> branchStrategyService.deleteStrategy(strategyId));

        verify(branchStrategyRepository).deleteById(strategyId);
    }

    @Test
    void should_throwNotFound_when_strategyMissing() {
        UUID missingId = UUID.randomUUID();

        when(branchStrategyRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> branchStrategyService.getStrategy(missingId));
    }

    @Test
    void should_throwNotFound_when_deletingMissingStrategy() {
        UUID missingId = UUID.randomUUID();

        when(branchStrategyRepository.existsById(missingId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> branchStrategyService.deleteStrategy(missingId));
    }

    @Test
    void should_throwNotFound_when_updatingMissingStrategy() {
        UUID missingId = UUID.randomUUID();
        CreateBranchStrategyRequest request = CreateBranchStrategyRequest.builder()
                .tenantId(UUID.randomUUID())
                .strategyType("TRUNK_BASED")
                .targetBranch("main")
                .build();

        when(branchStrategyRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> branchStrategyService.updateStrategy(missingId, request));
    }

    @Test
    void should_slugify_emptyOrBlankTitle() {
        assertEquals("unnamed", branchStrategyService.slugify(null));
        assertEquals("unnamed", branchStrategyService.slugify(""));
        assertEquals("unnamed", branchStrategyService.slugify("   "));
    }

    @Test
    void should_slugify_specialCharactersOnly() {
        assertEquals("unnamed", branchStrategyService.slugify("!@#$%^&*()"));
    }

    @Test
    void should_slugify_longTitle_truncatedTo50Chars() {
        String longTitle = "This is a very long task title that exceeds the fifty character limit for branch name slugs";
        String slug = branchStrategyService.slugify(longTitle);
        assertTrue(slug.length() <= 50);
        assertFalse(slug.endsWith("-"));
    }

    @Test
    void should_createStrategy_withDefaultPrefixAndTemplate_whenNullProvided() {
        UUID tenantId = UUID.randomUUID();

        CreateBranchStrategyRequest request = CreateBranchStrategyRequest.builder()
                .tenantId(tenantId)
                .strategyType("TRUNK_BASED")
                .targetBranch("main")
                .build();

        when(branchStrategyRepository.save(any(BranchStrategy.class))).thenAnswer(invocation -> {
            BranchStrategy entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            return entity;
        });

        BranchStrategyDto result = branchStrategyService.createStrategy(request);

        assertNotNull(result);
        assertEquals("squadron/", result.getBranchPrefix());
        assertEquals("{prefix}{taskId}/{slug}", result.getBranchNameTemplate());
    }
}
