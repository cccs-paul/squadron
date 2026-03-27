package com.squadron.identity.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SecurityGroupMemberTest {

    @Test
    void should_buildSecurityGroupMember_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Instant now = Instant.now();

        SecurityGroupMember member = SecurityGroupMember.builder()
                .id(id)
                .groupId(groupId)
                .memberType("USER")
                .memberId(memberId)
                .createdAt(now)
                .build();

        assertEquals(id, member.getId());
        assertEquals(groupId, member.getGroupId());
        assertEquals("USER", member.getMemberType());
        assertEquals(memberId, member.getMemberId());
        assertEquals(now, member.getCreatedAt());
    }

    @Test
    void should_setCreatedAt_when_onCreateCalled() {
        SecurityGroupMember member = SecurityGroupMember.builder()
                .groupId(UUID.randomUUID())
                .memberType("USER")
                .memberId(UUID.randomUUID())
                .build();

        assertNull(member.getCreatedAt());

        member.onCreate();

        assertNotNull(member.getCreatedAt());
    }

    @Test
    void should_createSecurityGroupMember_when_usingNoArgConstructor() {
        SecurityGroupMember member = new SecurityGroupMember();

        assertNull(member.getId());
        assertNull(member.getGroupId());
        assertNull(member.getMemberType());
        assertNull(member.getMemberId());
        assertNull(member.getCreatedAt());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        SecurityGroupMember member = new SecurityGroupMember();
        UUID id = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Instant now = Instant.now();

        member.setId(id);
        member.setGroupId(groupId);
        member.setMemberType("TEAM");
        member.setMemberId(memberId);
        member.setCreatedAt(now);

        assertEquals(id, member.getId());
        assertEquals(groupId, member.getGroupId());
        assertEquals("TEAM", member.getMemberType());
        assertEquals(memberId, member.getMemberId());
        assertEquals(now, member.getCreatedAt());
    }

    @Test
    void should_beEqual_when_sameFields() {
        UUID id = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Instant now = Instant.now();

        SecurityGroupMember m1 = SecurityGroupMember.builder()
                .id(id).groupId(groupId).memberType("USER").memberId(memberId).createdAt(now).build();
        SecurityGroupMember m2 = SecurityGroupMember.builder()
                .id(id).groupId(groupId).memberType("USER").memberId(memberId).createdAt(now).build();

        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentIds() {
        SecurityGroupMember m1 = SecurityGroupMember.builder().id(UUID.randomUUID()).memberType("USER").build();
        SecurityGroupMember m2 = SecurityGroupMember.builder().id(UUID.randomUUID()).memberType("USER").build();

        assertNotEquals(m1, m2);
    }

    @Test
    void should_generateToString_when_called() {
        UUID groupId = UUID.randomUUID();
        SecurityGroupMember member = SecurityGroupMember.builder()
                .groupId(groupId)
                .memberType("USER")
                .build();

        String toString = member.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("USER"));
        assertTrue(toString.contains(groupId.toString()));
    }

    @Test
    void should_supportTeamMemberType_when_specified() {
        SecurityGroupMember member = SecurityGroupMember.builder()
                .groupId(UUID.randomUUID())
                .memberType("TEAM")
                .memberId(UUID.randomUUID())
                .build();

        assertEquals("TEAM", member.getMemberType());
    }

    @Test
    void should_createSecurityGroupMember_when_usingAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Instant now = Instant.now();

        SecurityGroupMember member = new SecurityGroupMember(id, groupId, "USER", memberId, now);

        assertEquals(id, member.getId());
        assertEquals(groupId, member.getGroupId());
        assertEquals("USER", member.getMemberType());
        assertEquals(memberId, member.getMemberId());
        assertEquals(now, member.getCreatedAt());
    }
}
