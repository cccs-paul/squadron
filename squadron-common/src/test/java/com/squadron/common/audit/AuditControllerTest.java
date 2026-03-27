package com.squadron.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
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

    @Test
    void should_returnAuditEvents_when_queryByTenantId() throws Exception {
        storeTestEvent("TASK", "task-1");
        storeTestEvent("TASK", "task-2");

        mockMvc.perform(get("/api/audit")
                        .param("tenantId", tenantId.toString())
                        .param("page", "0")
                        .param("size", "50")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void should_returnAuditEvents_when_queryByUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        storeTestEventForUser(userId, "TASK", "task-1");
        storeTestEventForUser(userId, "TASK", "task-2");
        storeTestEventForUser(UUID.randomUUID(), "TASK", "task-3");

        mockMvc.perform(get("/api/audit/user/{userId}", userId)
                        .param("tenantId", tenantId.toString())
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
        storeTestEvent("TASK", "task-1");
        storeTestEvent("TASK", "task-1");
        storeTestEvent("TASK", "task-2");

        mockMvc.perform(get("/api/audit/resource/{resourceType}/{resourceId}", "TASK", "task-1")
                        .param("tenantId", tenantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void should_returnEmptyList_when_noEventsFound() throws Exception {
        mockMvc.perform(get("/api/audit")
                        .param("tenantId", UUID.randomUUID().toString())
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
        for (int i = 0; i < 5; i++) {
            storeTestEvent("TASK", "task-" + i);
        }

        mockMvc.perform(get("/api/audit")
                        .param("tenantId", tenantId.toString())
                        .param("page", "0")
                        .param("size", "3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3));

        mockMvc.perform(get("/api/audit")
                        .param("tenantId", tenantId.toString())
                        .param("page", "1")
                        .param("size", "3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void should_useDefaultPagination_when_noParamsProvided() throws Exception {
        storeTestEvent("TASK", "task-1");

        mockMvc.perform(get("/api/audit")
                        .param("tenantId", tenantId.toString())
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
