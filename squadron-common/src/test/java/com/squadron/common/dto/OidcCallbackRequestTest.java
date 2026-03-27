package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OidcCallbackRequestTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        OidcCallbackRequest request = OidcCallbackRequest.builder()
                .code("auth-code-123")
                .state("state-456")
                .redirectUri("https://app.example.com/callback")
                .build();

        assertEquals("auth-code-123", request.getCode());
        assertEquals("state-456", request.getState());
        assertEquals("https://app.example.com/callback", request.getRedirectUri());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        OidcCallbackRequest request = new OidcCallbackRequest();

        assertNull(request.getCode());
        assertNull(request.getState());
        assertNull(request.getRedirectUri());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        OidcCallbackRequest request = new OidcCallbackRequest(
                "code-abc", "state-xyz", "https://localhost/callback"
        );

        assertEquals("code-abc", request.getCode());
        assertEquals("state-xyz", request.getState());
        assertEquals("https://localhost/callback", request.getRedirectUri());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        OidcCallbackRequest request = new OidcCallbackRequest();
        request.setCode("new-code");
        request.setState("new-state");
        request.setRedirectUri("https://new-uri.com/callback");

        assertEquals("new-code", request.getCode());
        assertEquals("new-state", request.getState());
        assertEquals("https://new-uri.com/callback", request.getRedirectUri());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        OidcCallbackRequest request1 = OidcCallbackRequest.builder()
                .code("code")
                .state("state")
                .redirectUri("uri")
                .build();

        OidcCallbackRequest request2 = OidcCallbackRequest.builder()
                .code("code")
                .state("state")
                .redirectUri("uri")
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        OidcCallbackRequest request1 = OidcCallbackRequest.builder()
                .code("code-1")
                .state("state-1")
                .build();

        OidcCallbackRequest request2 = OidcCallbackRequest.builder()
                .code("code-2")
                .state("state-2")
                .build();

        assertNotEquals(request1, request2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        OidcCallbackRequest request = OidcCallbackRequest.builder()
                .code("my-code")
                .state("my-state")
                .build();

        String str = request.toString();
        assertTrue(str.contains("my-code"));
        assertTrue(str.contains("my-state"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        OidcCallbackRequest request = OidcCallbackRequest.builder()
                .code(null)
                .state(null)
                .redirectUri(null)
                .build();

        assertNull(request.getCode());
        assertNull(request.getState());
        assertNull(request.getRedirectUri());
    }

    @Test
    void should_allowNullRedirectUri_when_notProvided() {
        OidcCallbackRequest request = OidcCallbackRequest.builder()
                .code("code")
                .state("state")
                .build();

        assertNull(request.getRedirectUri());
        assertEquals("code", request.getCode());
        assertEquals("state", request.getState());
    }
}
