package com.squadron.identity.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserTeamTest {

    @Test
    void should_buildUserTeam_when_usingBuilder() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        UserTeam userTeam = UserTeam.builder()
                .userId(userId)
                .teamId(teamId)
                .role("LEAD")
                .build();

        assertEquals(userId, userTeam.getUserId());
        assertEquals(teamId, userTeam.getTeamId());
        assertEquals("LEAD", userTeam.getRole());
    }

    @Test
    void should_defaultRoleToMember_when_notSpecified() {
        UserTeam userTeam = UserTeam.builder()
                .userId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .build();

        assertEquals("MEMBER", userTeam.getRole());
    }

    @Test
    void should_allowOverridingDefaultRole_when_specified() {
        UserTeam userTeam = UserTeam.builder()
                .userId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .role("ADMIN")
                .build();

        assertEquals("ADMIN", userTeam.getRole());
    }

    @Test
    void should_createUserTeam_when_usingNoArgConstructor() {
        UserTeam userTeam = new UserTeam();

        assertNull(userTeam.getUserId());
        assertNull(userTeam.getTeamId());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        UserTeam userTeam = new UserTeam();
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        userTeam.setUserId(userId);
        userTeam.setTeamId(teamId);
        userTeam.setRole("REVIEWER");

        assertEquals(userId, userTeam.getUserId());
        assertEquals(teamId, userTeam.getTeamId());
        assertEquals("REVIEWER", userTeam.getRole());
    }

    @Test
    void should_beEqual_when_sameFields() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        UserTeam ut1 = UserTeam.builder().userId(userId).teamId(teamId).role("MEMBER").build();
        UserTeam ut2 = UserTeam.builder().userId(userId).teamId(teamId).role("MEMBER").build();

        assertEquals(ut1, ut2);
        assertEquals(ut1.hashCode(), ut2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentUserIds() {
        UUID teamId = UUID.randomUUID();

        UserTeam ut1 = UserTeam.builder().userId(UUID.randomUUID()).teamId(teamId).build();
        UserTeam ut2 = UserTeam.builder().userId(UUID.randomUUID()).teamId(teamId).build();

        assertNotEquals(ut1, ut2);
    }

    @Test
    void should_notBeEqual_when_differentTeamIds() {
        UUID userId = UUID.randomUUID();

        UserTeam ut1 = UserTeam.builder().userId(userId).teamId(UUID.randomUUID()).build();
        UserTeam ut2 = UserTeam.builder().userId(userId).teamId(UUID.randomUUID()).build();

        assertNotEquals(ut1, ut2);
    }

    @Test
    void should_generateToString_when_called() {
        UUID userId = UUID.randomUUID();
        UserTeam userTeam = UserTeam.builder().userId(userId).teamId(UUID.randomUUID()).build();

        String toString = userTeam.toString();

        assertNotNull(toString);
        assertTrue(toString.contains(userId.toString()));
    }

    @Test
    void should_createUserTeam_when_usingAllArgsConstructor() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        UserTeam userTeam = new UserTeam(userId, teamId, "LEAD");

        assertEquals(userId, userTeam.getUserId());
        assertEquals(teamId, userTeam.getTeamId());
        assertEquals("LEAD", userTeam.getRole());
    }
}
