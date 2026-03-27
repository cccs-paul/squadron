package com.squadron.identity.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamTest {

    @Test
    void should_buildTeam_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        Team team = Team.builder()
                .id(id)
                .tenantId(tenantId)
                .name("Backend Team")
                .settings("{\"config\":\"value\"}")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, team.getId());
        assertEquals(tenantId, team.getTenantId());
        assertEquals("Backend Team", team.getName());
        assertEquals("{\"config\":\"value\"}", team.getSettings());
        assertEquals(now, team.getCreatedAt());
        assertEquals(now, team.getUpdatedAt());
    }

    @Test
    void should_setTimestamps_when_onCreateCalled() {
        Team team = Team.builder()
                .name("QA Team")
                .tenantId(UUID.randomUUID())
                .build();

        assertNull(team.getCreatedAt());
        assertNull(team.getUpdatedAt());

        team.onCreate();

        assertNotNull(team.getCreatedAt());
        assertNotNull(team.getUpdatedAt());
        assertEquals(team.getCreatedAt(), team.getUpdatedAt());
    }

    @Test
    void should_updateTimestamp_when_onUpdateCalled() throws InterruptedException {
        Team team = Team.builder().name("Test").build();
        team.onCreate();
        Instant originalUpdatedAt = team.getUpdatedAt();

        Thread.sleep(10);
        team.onUpdate();

        assertEquals(team.getCreatedAt(), originalUpdatedAt);
        assertTrue(team.getUpdatedAt().isAfter(originalUpdatedAt));
    }

    @Test
    void should_createTeam_when_usingNoArgConstructor() {
        Team team = new Team();

        assertNull(team.getId());
        assertNull(team.getTenantId());
        assertNull(team.getName());
        assertNull(team.getSettings());
        assertNull(team.getCreatedAt());
        assertNull(team.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        Team team = new Team();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        team.setId(id);
        team.setTenantId(tenantId);
        team.setName("New Team");
        team.setSettings("{\"key\":\"val\"}");
        team.setCreatedAt(now);
        team.setUpdatedAt(now);

        assertEquals(id, team.getId());
        assertEquals(tenantId, team.getTenantId());
        assertEquals("New Team", team.getName());
        assertEquals("{\"key\":\"val\"}", team.getSettings());
        assertEquals(now, team.getCreatedAt());
        assertEquals(now, team.getUpdatedAt());
    }

    @Test
    void should_beEqual_when_sameFields() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Team t1 = Team.builder().id(id).tenantId(tenantId).name("Team").build();
        Team t2 = Team.builder().id(id).tenantId(tenantId).name("Team").build();

        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentIds() {
        Team t1 = Team.builder().id(UUID.randomUUID()).name("Team").build();
        Team t2 = Team.builder().id(UUID.randomUUID()).name("Team").build();

        assertNotEquals(t1, t2);
    }

    @Test
    void should_generateToString_when_called() {
        Team team = Team.builder().name("Backend").build();

        String toString = team.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("Backend"));
    }

    @Test
    void should_createTeam_when_usingAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        Team team = new Team(id, tenantId, "All Args Team", "{}", now, now);

        assertEquals(id, team.getId());
        assertEquals(tenantId, team.getTenantId());
        assertEquals("All Args Team", team.getName());
    }
}
