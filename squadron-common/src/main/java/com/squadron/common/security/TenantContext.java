package com.squadron.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Thread-local context holding the current user's identity and tenant information.
 * Populated by TenantFilter from headers set by the gateway.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantContext {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private UUID tenantId;
    private UUID userId;
    private String email;
    private Set<String> roles;
    private String authProvider;

    public static void setContext(TenantContext context) {
        CONTEXT.set(context);
    }

    public static TenantContext getContext() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static UUID getTenantId() {
        TenantContext ctx = CONTEXT.get();
        return ctx != null ? ctx.tenantId : null;
    }

    public static UUID getUserId() {
        TenantContext ctx = CONTEXT.get();
        return ctx != null ? ctx.userId : null;
    }

    public static String getEmail() {
        TenantContext ctx = CONTEXT.get();
        return ctx != null ? ctx.email : null;
    }

    public static Set<String> getRoles() {
        TenantContext ctx = CONTEXT.get();
        return ctx != null && ctx.roles != null ? ctx.roles : Collections.emptySet();
    }

    public static String getAuthProvider() {
        TenantContext ctx = CONTEXT.get();
        return ctx != null ? ctx.authProvider : null;
    }

    public static boolean hasRole(String role) {
        return getRoles().contains(role);
    }

    public static boolean isAdmin() {
        return hasRole(SecurityConstants.ROLE_ADMIN);
    }
}
