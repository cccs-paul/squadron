package com.squadron.git.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BranchStrategyTest {

    @Test
    void should_createBranchStrategy_withBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();

        BranchStrategy strategy = BranchStrategy.builder()
                .id(id)
                .tenantId(tenantId)
                .projectId(projectId)
                .strategyType("GITFLOW")
                .branchPrefix("feature/")
                .targetBranch("develop")
                .developmentBranch("develop")
                .branchNameTemplate("{prefix}{taskId}/{slug}")
                .createdAt(now)
                .build();

        assertEquals(id, strategy.getId());
        assertEquals(tenantId, strategy.getTenantId());
        assertEquals(projectId, strategy.getProjectId());
        assertEquals("GITFLOW", strategy.getStrategyType());
        assertEquals("feature/", strategy.getBranchPrefix());
        assertEquals("develop", strategy.getTargetBranch());
        assertEquals("develop", strategy.getDevelopmentBranch());
        assertEquals("{prefix}{taskId}/{slug}", strategy.getBranchNameTemplate());
        assertEquals(now, strategy.getCreatedAt());
    }

    @Test
    void should_haveDefaultValues_whenUsingBuilder() {
        BranchStrategy strategy = BranchStrategy.builder()
                .tenantId(UUID.randomUUID())
                .strategyType("TRUNK_BASED")
                .build();

        assertEquals("squadron/", strategy.getBranchPrefix());
        assertEquals("main", strategy.getTargetBranch());
        assertEquals("{prefix}{taskId}/{slug}", strategy.getBranchNameTemplate());
        assertNull(strategy.getProjectId());
        assertNull(strategy.getDevelopmentBranch());
    }

    @Test
    void should_setCreatedAt_onPrePersist() {
        BranchStrategy strategy = BranchStrategy.builder()
                .tenantId(UUID.randomUUID())
                .strategyType("TRUNK_BASED")
                .build();

        assertNull(strategy.getCreatedAt());

        strategy.onCreate();

        assertNotNull(strategy.getCreatedAt());
    }

    @Test
    void should_setAndGetAllFields() {
        BranchStrategy strategy = new BranchStrategy();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();

        strategy.setId(id);
        strategy.setTenantId(tenantId);
        strategy.setProjectId(projectId);
        strategy.setStrategyType("CUSTOM");
        strategy.setBranchPrefix("custom/");
        strategy.setTargetBranch("release");
        strategy.setDevelopmentBranch("dev");
        strategy.setBranchNameTemplate("{prefix}{slug}");
        strategy.setCreatedAt(now);

        assertEquals(id, strategy.getId());
        assertEquals(tenantId, strategy.getTenantId());
        assertEquals(projectId, strategy.getProjectId());
        assertEquals("CUSTOM", strategy.getStrategyType());
        assertEquals("custom/", strategy.getBranchPrefix());
        assertEquals("release", strategy.getTargetBranch());
        assertEquals("dev", strategy.getDevelopmentBranch());
        assertEquals("{prefix}{slug}", strategy.getBranchNameTemplate());
        assertEquals(now, strategy.getCreatedAt());
    }

    @Test
    void should_supportAllStrategyTypes() {
        for (String type : new String[]{"GITFLOW", "TRUNK_BASED", "CUSTOM"}) {
            BranchStrategy strategy = BranchStrategy.builder()
                    .tenantId(UUID.randomUUID())
                    .strategyType(type)
                    .build();
            assertEquals(type, strategy.getStrategyType());
        }
    }

    @Test
    void should_allowNullProjectId_forTenantDefault() {
        BranchStrategy strategy = BranchStrategy.builder()
                .tenantId(UUID.randomUUID())
                .projectId(null)
                .strategyType("TRUNK_BASED")
                .build();

        assertNull(strategy.getProjectId());
    }
}
