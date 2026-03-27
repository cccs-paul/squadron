package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginRequestTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        LoginRequest request = LoginRequest.builder()
                .username("admin")
                .password("secret123")
                .tenantSlug("acme-corp")
                .build();

        assertEquals("admin", request.getUsername());
        assertEquals("secret123", request.getPassword());
        assertEquals("acme-corp", request.getTenantSlug());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        LoginRequest request = new LoginRequest();

        assertNull(request.getUsername());
        assertNull(request.getPassword());
        assertNull(request.getTenantSlug());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        LoginRequest request = new LoginRequest("user1", "pass1", "tenant-slug");

        assertEquals("user1", request.getUsername());
        assertEquals("pass1", request.getPassword());
        assertEquals("tenant-slug", request.getTenantSlug());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        LoginRequest request = new LoginRequest();
        request.setUsername("john");
        request.setPassword("p@ssw0rd");
        request.setTenantSlug("my-tenant");

        assertEquals("john", request.getUsername());
        assertEquals("p@ssw0rd", request.getPassword());
        assertEquals("my-tenant", request.getTenantSlug());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        LoginRequest request1 = LoginRequest.builder()
                .username("admin")
                .password("pass")
                .tenantSlug("slug")
                .build();

        LoginRequest request2 = LoginRequest.builder()
                .username("admin")
                .password("pass")
                .tenantSlug("slug")
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        LoginRequest request1 = LoginRequest.builder()
                .username("admin")
                .password("pass1")
                .build();

        LoginRequest request2 = LoginRequest.builder()
                .username("admin")
                .password("pass2")
                .build();

        assertNotEquals(request1, request2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .tenantSlug("acme")
                .build();

        String str = request.toString();
        assertTrue(str.contains("testuser"));
        assertTrue(str.contains("acme"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        LoginRequest request = LoginRequest.builder()
                .username(null)
                .password(null)
                .tenantSlug(null)
                .build();

        assertNull(request.getUsername());
        assertNull(request.getPassword());
        assertNull(request.getTenantSlug());
    }

    @Test
    void should_allowNullTenantSlug_when_singleTenantMode() {
        LoginRequest request = LoginRequest.builder()
                .username("user")
                .password("pass")
                .build();

        assertNull(request.getTenantSlug());
        assertEquals("user", request.getUsername());
        assertEquals("pass", request.getPassword());
    }
}
