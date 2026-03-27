package com.squadron.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.review.config.SecurityConfig;
import com.squadron.review.dto.QAReportDto;
import com.squadron.review.entity.QAReport;
import com.squadron.review.service.QAReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QAReportController.class)
@ContextConfiguration(classes = {QAReportController.class, SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class QAReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private QAReportService qaReportService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(roles = {"developer"})
    void should_createReport() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        QAReportDto dto = QAReportDto.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("PASS")
                .summary("All tests pass")
                .lineCoverage(85.0)
                .branchCoverage(75.0)
                .testsPassed(100)
                .testsFailed(0)
                .testsSkipped(5)
                .build();

        QAReport savedReport = QAReport.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("PASS")
                .summary("All tests pass")
                .lineCoverage(85.0)
                .branchCoverage(75.0)
                .testsPassed(100)
                .testsFailed(0)
                .testsSkipped(5)
                .createdAt(Instant.now())
                .build();

        when(qaReportService.createReport(any(QAReportDto.class))).thenReturn(savedReport);

        mockMvc.perform(post("/api/qa-reports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verdict").value("PASS"))
                .andExpect(jsonPath("$.data.testsPassed").value(100));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getReportsForTask() throws Exception {
        UUID taskId = UUID.randomUUID();

        QAReport report = QAReport.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .taskId(taskId)
                .verdict("PASS")
                .summary("All good")
                .createdAt(Instant.now())
                .build();

        when(qaReportService.getReportsForTask(taskId)).thenReturn(List.of(report));

        mockMvc.perform(get("/api/qa-reports/task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].verdict").value("PASS"));
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_getLatestReport() throws Exception {
        UUID taskId = UUID.randomUUID();

        QAReport report = QAReport.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .taskId(taskId)
                .verdict("CONDITIONAL_PASS")
                .summary("Minor issues")
                .createdAt(Instant.now())
                .build();

        when(qaReportService.getLatestReport(taskId)).thenReturn(report);

        mockMvc.perform(get("/api/qa-reports/task/{taskId}/latest", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verdict").value("CONDITIONAL_PASS"));
    }

    @Test
    @WithMockUser(roles = {"qa"})
    void should_checkQAGate() throws Exception {
        UUID taskId = UUID.randomUUID();

        when(qaReportService.checkQAGate(taskId)).thenReturn(true);

        mockMvc.perform(get("/api/qa-reports/task/{taskId}/gate", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void should_requireAuthentication() throws Exception {
        mockMvc.perform(get("/api/qa-reports/task/{taskId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
