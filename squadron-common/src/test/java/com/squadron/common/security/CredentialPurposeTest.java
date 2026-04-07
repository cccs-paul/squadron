package com.squadron.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CredentialPurposeTest {

    @Test
    void should_haveGitCloneValue_when_enumDefined() {
        assertEquals("GIT_CLONE", CredentialPurpose.GIT_CLONE.name());
    }

    @Test
    void should_haveGitPushValue_when_enumDefined() {
        assertEquals("GIT_PUSH", CredentialPurpose.GIT_PUSH.name());
    }

    @Test
    void should_havePlatformApiValue_when_enumDefined() {
        assertEquals("PLATFORM_API", CredentialPurpose.PLATFORM_API.name());
    }

    @Test
    void should_haveFullValue_when_enumDefined() {
        assertEquals("FULL", CredentialPurpose.FULL.name());
    }

    @Test
    void should_haveReviewBotValue_when_enumDefined() {
        assertEquals("REVIEW_BOT", CredentialPurpose.REVIEW_BOT.name());
    }

    @Test
    void should_haveFiveValues_when_enumDefined() {
        CredentialPurpose[] values = CredentialPurpose.values();

        assertEquals(5, values.length);
    }

    @Test
    void should_returnCorrectEnum_when_valueOfCalled() {
        assertEquals(CredentialPurpose.GIT_CLONE, CredentialPurpose.valueOf("GIT_CLONE"));
        assertEquals(CredentialPurpose.GIT_PUSH, CredentialPurpose.valueOf("GIT_PUSH"));
        assertEquals(CredentialPurpose.PLATFORM_API, CredentialPurpose.valueOf("PLATFORM_API"));
        assertEquals(CredentialPurpose.FULL, CredentialPurpose.valueOf("FULL"));
        assertEquals(CredentialPurpose.REVIEW_BOT, CredentialPurpose.valueOf("REVIEW_BOT"));
    }

    @Test
    void should_throwIllegalArgumentException_when_invalidValueOfCalled() {
        assertThrows(IllegalArgumentException.class, () -> CredentialPurpose.valueOf("UNKNOWN"));
    }

    @Test
    void should_haveCorrectOrdinalOrder_when_enumDefined() {
        assertTrue(CredentialPurpose.GIT_CLONE.ordinal() < CredentialPurpose.GIT_PUSH.ordinal());
        assertTrue(CredentialPurpose.GIT_PUSH.ordinal() < CredentialPurpose.PLATFORM_API.ordinal());
        assertTrue(CredentialPurpose.PLATFORM_API.ordinal() < CredentialPurpose.FULL.ordinal());
        assertTrue(CredentialPurpose.FULL.ordinal() < CredentialPurpose.REVIEW_BOT.ordinal());
    }
}
