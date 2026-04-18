package com.squadron.common.audit;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.security.SecurityConstants;
import com.squadron.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuditControllerTest {

    private MockMvc mockMvc;
    private AuditQueryService auditQueryService;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        auditQueryService = new AuditQueryService();
        AuditController controller = new AuditController(auditQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        tenantId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void setTenantContext() {
        TenantContext context = TenantContext.builder()
                .tenantId(tenantId)
                .userId(UUID.randomUUID())
                .roles(Collections.emptySet())
                .build();
        TenantContext.setContext(context);
    }

    @Test
    void should_returnAuditEvents_when_queryByTenantId() throws Exception {
        setTenantContext();
        storeTestEvent("TASK", "task-1");
        storeTestEvent("TASK", "task-2");

        mockMvc.perform(get("/api/audit")
                        .param("page", "0")
                        .param("size", "50")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void should_return403_when_noTenantContext() throws Exception {
        mockMvc.perform(get("/api/audit")
                        .param("page", "0")
                        .param("size", "50")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_returnAuditEvents_when_queryByUserId() throws Exception {
        setTenantContext();
        UUID userId = UUID.randomUUID();
        storeTestEventForUser(userId, "TASK", "task-1");
        storeTestEventForUser(userId, "TASK", "task-2");
        storeTestEventForUser(UUID.randomUUID(), "TASK", "task-3");

        mockMvc.perform(get("/api/audit/user/{userId}", userId)
                        .param("page", "0")
                        .param("size", "50")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void should_returnAuditEvents_when_queryByResource() throws Exception {
        setTenantContext();
        storeTestEvent("TASK", "task-1");
        storeTestEvent("TASK", "task-1");
        storeTestEvent("TASK", "task-2");

        mockMvc.perform(get("/api/audit/resource/{resourceType}/{resourceId}", "TASK", "task-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void should_returnEmptyList_when_noEventsFound() throws Exception {
        setTenantContext();
        mockMvc.perform(get("/api/audit")
                        .param("page", "0")
                        .param("size", "50")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void should_supportPagination_when_queryByTenantId() throws Exception {
        setTenantContext();
        for (int i = 0; i < 5; i++) {
            storeTestEvent("TASK", "task-" + i);
        }

        mockMvc.perform(get("/api/audit")
                        .param("page", "0")
                        .param("size", "3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3));

        mockMvc.perform(get("/api/audit")
                        .param("page", "1")
                        .param("size", "3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void should_useDefaultPagination_when_noParamsProvided() throws Exception {
        setTenantContext();
        storeTestEvent("TASK", "task-1");

        mockMvc.perform(get("/api/audit")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    private void storeTestEvent(String resourceType, String resourceId) {
        storeTestEventForUser(UUID.randomUUID(), resourceType, resourceId);
    }

    private void storeTestEventForUser(UUID userId, String resourceType, String resourceId) {
        auditQueryService.store(AuditEvent.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(userId)
                .action("TEST_ACTION")
                .resourceType(resourceType)
                .resourceId(resourceId)
                .auditAction(AuditAction.CREATE)
                .timestamp(Instant.now())
                .build());
    }
}
