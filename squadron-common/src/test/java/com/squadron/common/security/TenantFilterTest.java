package com.squadron.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantFilterTest {

    private TenantFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new TenantFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void should_populateContext_when_allHeadersPresent() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        request.addHeader(SecurityConstants.HEADER_TENANT_ID, tenantId.toString());
        request.addHeader(SecurityConstants.HEADER_USER_ID, userId.toString());
        request.addHeader(SecurityConstants.HEADER_USER_EMAIL, "test@example.com");
        request.addHeader(SecurityConstants.HEADER_USER_ROLES, "developer,qa");
        request.addHeader(SecurityConstants.HEADER_AUTH_PROVIDER, "ldap");

        // Capture context during filter chain execution
        doAnswer(invocation -> {
            assertEquals(tenantId, TenantContext.getTenantId());
            assertEquals(userId, TenantContext.getUserId());
            assertEquals("test@example.com", TenantContext.getEmail());
            assertTrue(TenantContext.getRoles().contains("developer"));
            assertTrue(TenantContext.getRoles().contains("qa"));
            assertEquals("ldap", TenantContext.getAuthProvider());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_clearContextAfterFilter_when_requestCompletes() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        request.addHeader(SecurityConstants.HEADER_TENANT_ID, tenantId.toString());

        filter.doFilterInternal(request, response, filterChain);

        // After the filter completes, the context should be cleared
        assertNull(TenantContext.getContext());
    }

    @Test
    void should_notSetContext_when_noTenantIdHeader() throws ServletException, IOException {
        request.addHeader(SecurityConstants.HEADER_USER_EMAIL, "test@example.com");

        doAnswer(invocation -> {
            assertNull(TenantContext.getContext());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_notSetContext_when_tenantIdHeaderIsBlank() throws ServletException, IOException {
        request.addHeader(SecurityConstants.HEADER_TENANT_ID, "  ");

        doAnswer(invocation -> {
            assertNull(TenantContext.getContext());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_handleNullUserId_when_userIdHeaderMissing() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        request.addHeader(SecurityConstants.HEADER_TENANT_ID, tenantId.toString());

        doAnswer(invocation -> {
            assertEquals(tenantId, TenantContext.getTenantId());
            assertNull(TenantContext.getUserId());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void should_parseEmptyRoles_when_rolesHeaderIsNull() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        request.addHeader(SecurityConstants.HEADER_TENANT_ID, tenantId.toString());

        doAnswer(invocation -> {
            assertTrue(TenantContext.getRoles().isEmpty());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void should_parseEmptyRoles_when_rolesHeaderIsBlank() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        request.addHeader(SecurityConstants.HEADER_TENANT_ID, tenantId.toString());
        request.addHeader(SecurityConstants.HEADER_USER_ROLES, "  ");

        doAnswer(invocation -> {
            assertTrue(TenantContext.getRoles().isEmpty());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void should_trimRoles_when_rolesHaveSpaces() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        request.addHeader(SecurityConstants.HEADER_TENANT_ID, tenantId.toString());
        request.addHeader(SecurityConstants.HEADER_USER_ROLES, " developer , qa , admin ");

        doAnswer(invocation -> {
            var roles = TenantContext.getRoles();
            assertEquals(3, roles.size());
            assertTrue(roles.contains("developer"));
            assertTrue(roles.contains("qa"));
            assertTrue(roles.contains("admin"));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void should_clearContext_when_filterChainThrowsException() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        request.addHeader(SecurityConstants.HEADER_TENANT_ID, tenantId.toString());

        doThrow(new RuntimeException("filter error")).when(filterChain).doFilter(request, response);

        assertThrows(RuntimeException.class, () -> filter.doFilterInternal(request, response, filterChain));
        assertNull(TenantContext.getContext());
    }
}
