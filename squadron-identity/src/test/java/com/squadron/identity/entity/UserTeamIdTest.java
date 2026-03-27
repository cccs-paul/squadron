package com.squadron.identity.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserTeamIdTest {

    @Test
    void should_createUserTeamId_when_usingAllArgsConstructor() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        UserTeamId id = new UserTeamId(userId, teamId);

        assertEquals(userId, id.getUserId());
        assertEquals(teamId, id.getTeamId());
    }

    @Test
    void should_createUserTeamId_when_usingNoArgConstructor() {
        UserTeamId id = new UserTeamId();

        assertNull(id.getUserId());
        assertNull(id.getTeamId());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        UserTeamId id = new UserTeamId();
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        id.setUserId(userId);
        id.setTeamId(teamId);

        assertEquals(userId, id.getUserId());
        assertEquals(teamId, id.getTeamId());
    }

    @Test
    void should_beEqual_when_sameUserIdAndTeamId() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        UserTeamId id1 = new UserTeamId(userId, teamId);
        UserTeamId id2 = new UserTeamId(userId, teamId);

        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentUserId() {
        UUID teamId = UUID.randomUUID();

        UserTeamId id1 = new UserTeamId(UUID.randomUUID(), teamId);
        UserTeamId id2 = new UserTeamId(UUID.randomUUID(), teamId);

        assertNotEquals(id1, id2);
    }

    @Test
    void should_notBeEqual_when_differentTeamId() {
        UUID userId = UUID.randomUUID();

        UserTeamId id1 = new UserTeamId(userId, UUID.randomUUID());
        UserTeamId id2 = new UserTeamId(userId, UUID.randomUUID());

        assertNotEquals(id1, id2);
    }

    @Test
    void should_notBeEqual_when_comparedToNull() {
        UserTeamId id = new UserTeamId(UUID.randomUUID(), UUID.randomUUID());

        assertNotEquals(null, id);
    }

    @Test
    void should_notBeEqual_when_comparedToDifferentType() {
        UserTeamId id = new UserTeamId(UUID.randomUUID(), UUID.randomUUID());

        assertNotEquals("not-a-user-team-id", id);
    }

    @Test
    void should_beEqual_when_comparedToSelf() {
        UserTeamId id = new UserTeamId(UUID.randomUUID(), UUID.randomUUID());

        assertEquals(id, id);
    }

    @Test
    void should_implementSerializable_when_checked() {
        UserTeamId id = new UserTeamId(UUID.randomUUID(), UUID.randomUUID());

        assertInstanceOf(java.io.Serializable.class, id);
    }

    @Test
    void should_generateToString_when_called() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UserTeamId id = new UserTeamId(userId, teamId);

        String toString = id.toString();

        assertNotNull(toString);
        assertTrue(toString.contains(userId.toString()));
        assertTrue(toString.contains(teamId.toString()));
    }
}
