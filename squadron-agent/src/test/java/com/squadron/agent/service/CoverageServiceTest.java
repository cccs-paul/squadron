package com.squadron.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squadron.agent.tool.builtin.ExecResultDto;
import com.squadron.agent.tool.builtin.WorkspaceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoverageServiceTest {

    @Mock
    private WorkspaceClient workspaceClient;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private CoverageService coverageService;

    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        coverageService = new CoverageService(workspaceClient, objectMapper);
        workspaceId = UUID.randomUUID();
    }

    // ---------------------------------------------------------------------------
    // detectProjectType tests
    // ---------------------------------------------------------------------------

    @Test
    void should_detectProjectType_java() {
        ExecResultDto result = ExecResultDto.builder()
                .exitCode(0).stdout("pom.xml\nsrc\nREADME.md").stderr("").build();
        when(workspaceClient.exec(workspaceId, "ls", "/workspace")).thenReturn(result);

        assertEquals("java", coverageService.detectProjectType(workspaceId));
    }

    @Test
    void should_detectProjectType_javaGradle() {
        ExecResultDto result = ExecResultDto.builder()
                .exitCode(0).stdout("build.gradle\nsrc\nREADME.md").stderr("").build();
        when(workspaceClient.exec(workspaceId, "ls", "/workspace")).thenReturn(result);

        assertEquals("java", coverageService.detectProjectType(workspaceId));
    }

    @Test
    void should_detectProjectType_javascript() {
        ExecResultDto result = ExecResultDto.builder()
                .exitCode(0).stdout("package.json\nnode_modules\nsrc\nREADME.md").stderr("").build();
        when(workspaceClient.exec(workspaceId, "ls", "/workspace")).thenReturn(result);

        assertEquals("javascript", coverageService.detectProjectType(workspaceId));
    }

    @Test
    void should_detectProjectType_python() {
        ExecResultDto result = ExecResultDto.builder()
                .exitCode(0).stdout("requirements.txt\napp.py\nREADME.md").stderr("").build();
        when(workspaceClient.exec(workspaceId, "ls", "/workspace")).thenReturn(result);

        assertEquals("python", coverageService.detectProjectType(workspaceId));
    }

    @Test
    void should_detectProjectType_unknown() {
        ExecResultDto result = ExecResultDto.builder()
                .exitCode(0).stdout("README.md\nLICENSE").stderr("").build();
        when(workspaceClient.exec(workspaceId, "ls", "/workspace")).thenReturn(result);

        assertEquals("unknown", coverageService.detectProjectType(workspaceId));
    }

    @Test
    void should_detectProjectType_onFailure() {
        when(workspaceClient.exec(workspaceId, "ls", "/workspace"))
                .thenThrow(new RuntimeException("Connection refused"));

        assertEquals("unknown", coverageService.detectProjectType(workspaceId));
    }

    @Test
    void should_detectProjectType_onNonZeroExit() {
        ExecResultDto result = ExecResultDto.builder()
                .exitCode(1).stdout("").stderr("No such directory").build();
        when(workspaceClient.exec(workspaceId, "ls", "/workspace")).thenReturn(result);

        assertEquals("unknown", coverageService.detectProjectType(workspaceId));
    }

    // ---------------------------------------------------------------------------
    // collectJacocoCoverage tests
    // ---------------------------------------------------------------------------

    @Test
    void should_collectJacocoCoverage_successfully() {
        String jacocoCsv = "GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,"
                + "BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,"
                + "COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED\n"
                + "MyProject,com.example,Foo,10,90,2,8,5,45,3,7,1,9\n"
                + "MyProject,com.example,Bar,20,80,4,6,10,40,2,8,2,8\n";

        ExecResultDto result = ExecResultDto.builder()
                .exitCode(0).stdout(jacocoCsv).stderr("").build();
        when(workspaceClient.exec(workspaceId, "cat",
                "/workspace/target/site/jacoco/jacoco.csv")).thenReturn(result);

        CoverageService.CoverageReport report = coverageService.collectJacocoCoverage(workspaceId);

        assertEquals("jacoco", report.getReportFormat());
        // Total lines: (5+45) + (10+40) = 100, covered: 45+40 = 85
        assertEquals(85.0, report.getLineCoverage(), 0.01);
        assertEquals(100, report.getTotalLines());
        assertEquals(85, report.getCoveredLines());
        // Total branches: (2+8) + (4+6) = 20, covered: 8+6 = 14
        assertEquals(70.0, report.getBranchCoverage(), 0.01);
        assertEquals(20, report.getTotalBranches());
        assertEquals(14, report.getCoveredBranches());
        // Total methods: (1+9) + (2+8) = 20, covered: 9+8 = 17
        assertEquals(85.0, report.getMethodCoverage(), 0.01);
        assertEquals(20, report.getTotalMethods());
        assertEquals(17, report.getCoveredMethods());
        // Packages
        assertNotNull(report.getPackages());
        assertEquals(1, report.getPackages().size()); // Same package "com.example"
        assertEquals("com.example", report.getPackages().get(0).getPackageName());
    }

    @Test
    void should_collectJacocoCoverage_onFailure() {
        ExecResultDto result = ExecResultDto.builder()
                .exitCode(1).stdout("").stderr("No such file").build();
        when(workspaceClient.exec(workspaceId, "cat",
                "/workspace/target/site/jacoco/jacoco.csv")).thenReturn(result);

        CoverageService.CoverageReport report = coverageService.collectJacocoCoverage(workspaceId);

        assertEquals("jacoco", report.getReportFormat());
        assertEquals(0.0, report.getLineCoverage(), 0.01);
    }

    @Test
    void should_collectJacocoCoverage_onException() {
        when(workspaceClient.exec(workspaceId, "cat",
                "/workspace/target/site/jacoco/jacoco.csv"))
                .thenThrow(new RuntimeException("Connection error"));

        CoverageService.CoverageReport report = coverageService.collectJacocoCoverage(workspaceId);

        assertEquals("jacoco", report.getReportFormat());
        assertEquals(0.0, report.getLineCoverage(), 0.01);
    }

    // ---------------------------------------------------------------------------
    // collectIstanbulCoverage tests
    // ---------------------------------------------------------------------------

    @Test
    void should_collectIstanbulCoverage_successfully() {
        String istanbulJson = "{\"total\": {"
                + "\"lines\": {\"total\": 100, \"covered\": 85, \"pct\": 85},"
                + "\"statements\": {\"total\": 120, \"covered\": 100, \"pct\": 83.33},"
                + "\"functions\": {\"total\": 30, \"covered\": 25, \"pct\": 83.33},"
                + "\"branches\": {\"total\": 20, \"covered\": 15, \"pct\": 75}"
                + "}}";

        ExecResultDto result = ExecResultDto.builder()
                .exitCode(0).stdout(istanbulJson).stderr("").build();
        when(workspaceClient.exec(workspaceId, "cat",
                "/workspace/coverage/coverage-summary.json")).thenReturn(result);

        CoverageService.CoverageReport report = coverageService.collectIstanbulCoverage(workspaceId);

        assertEquals("istanbul", report.getReportFormat());
        assertEquals(85.0, report.getLineCoverage(), 0.01);
        assertEquals(83.33, report.getStatementCoverage(), 0.01);
        assertEquals(83.33, report.getMethodCoverage(), 0.01);
        assertEquals(75.0, report.getBranchCoverage(), 0.01);
        assertEquals(100, report.getTotalLines());
        assertEquals(85, report.getCoveredLines());
        assertEquals(20, report.getTotalBranches());
        assertEquals(15, report.getCoveredBranches());
        assertEquals(30, report.getTotalMethods());
        assertEquals(25, report.getCoveredMethods());
    }

    @Test
    void should_collectIstanbulCoverage_onFailure() {
        when(workspaceClient.exec(workspaceId, "cat",
                "/workspace/coverage/coverage-summary.json"))
                .thenThrow(new RuntimeException("Connection error"));

        CoverageService.CoverageReport report = coverageService.collectIstanbulCoverage(workspaceId);

        assertEquals("istanbul", report.getReportFormat());
        assertEquals(0.0, report.getLineCoverage(), 0.01);
    }

    @Test
    void should_collectIstanbulCoverage_onNonZeroExit() {
        ExecResultDto result = ExecResultDto.builder()
                .exitCode(1).stdout("").stderr("File not found").build();
        when(workspaceClient.exec(workspaceId, "cat",
                "/workspace/coverage/coverage-summary.json")).thenReturn(result);

        CoverageService.CoverageReport report = coverageService.collectIstanbulCoverage(workspaceId);

        assertEquals("istanbul", report.getReportFormat());
        assertEquals(0.0, report.getLineCoverage(), 0.01);
    }

    // ---------------------------------------------------------------------------
    // collectPythonCoverage tests
    // ---------------------------------------------------------------------------

    @Test
    void should_collectPythonCoverage_successfully() {
        String pythonJson = "{\"totals\": {"
                + "\"percent_covered\": 78.5,"
                + "\"covered_lines\": 157,"
                + "\"missing_lines\": 43,"
                + "\"covered_branches\": 30,"
                + "\"missing_branches\": 10"
                + "}}";

        ExecResultDto result = ExecResultDto.builder()
                .exitCode(0).stdout(pythonJson).stderr("").build();
        when(workspaceClient.exec(workspaceId, "cat", "/workspace/coverage.json"))
                .thenReturn(result);

        CoverageService.CoverageReport report = coverageService.collectPythonCoverage(workspaceId);

        assertEquals("coverage.py", report.getReportFormat());
        assertEquals(78.5, report.getLineCoverage(), 0.01);
        assertEquals(200, report.getTotalLines()); // 157 + 43
        assertEquals(157, report.getCoveredLines());
        assertEquals(40, report.getTotalBranches()); // 30 + 10
        assertEquals(30, report.getCoveredBranches());
        assertEquals(75.0, report.getBranchCoverage(), 0.01); // 30/40 = 75%
    }

    @Test
    void should_collectPythonCoverage_onFailure() {
        // First call (cat) fails, then fallback (bash -c) also fails
        ExecResultDto catResult = ExecResultDto.builder()
                .exitCode(1).stdout("").stderr("No such file").build();
        ExecResultDto fallbackResult = ExecResultDto.builder()
                .exitCode(1).stdout("").stderr("coverage not installed").build();

        when(workspaceClient.exec(workspaceId, "cat", "/workspace/coverage.json"))
                .thenReturn(catResult);
        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), anyString()))
                .thenReturn(fallbackResult);

        CoverageService.CoverageReport report = coverageService.collectPythonCoverage(workspaceId);

        assertEquals("coverage.py", report.getReportFormat());
        assertEquals(0.0, report.getLineCoverage(), 0.01);
    }

    // ---------------------------------------------------------------------------
    // collectCoverage (dispatch) tests
    // ---------------------------------------------------------------------------

    @Test
    void should_collectCoverage_java() {
        // detectProjectType => "java"
        ExecResultDto lsResult = ExecResultDto.builder()
                .exitCode(0).stdout("pom.xml\nsrc").stderr("").build();
        when(workspaceClient.exec(workspaceId, "ls", "/workspace")).thenReturn(lsResult);

        // collectJacocoCoverage
        String jacocoCsv = "GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,"
                + "BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,"
                + "COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED\n"
                + "Proj,com.test,TestClass,5,95,1,9,2,48,1,9,0,10\n";
        ExecResultDto jacocoResult = ExecResultDto.builder()
                .exitCode(0).stdout(jacocoCsv).stderr("").build();
        when(workspaceClient.exec(workspaceId, "cat",
                "/workspace/target/site/jacoco/jacoco.csv")).thenReturn(jacocoResult);

        CoverageService.CoverageReport report = coverageService.collectCoverage(workspaceId);

        assertEquals("jacoco", report.getReportFormat());
        assertTrue(report.getLineCoverage() > 0);
    }

    @Test
    void should_collectCoverage_unknown() {
        ExecResultDto lsResult = ExecResultDto.builder()
                .exitCode(0).stdout("README.md\nLICENSE").stderr("").build();
        when(workspaceClient.exec(workspaceId, "ls", "/workspace")).thenReturn(lsResult);

        CoverageService.CoverageReport report = coverageService.collectCoverage(workspaceId);

        assertEquals("unknown", report.getReportFormat());
        assertEquals(0.0, report.getLineCoverage(), 0.01);
    }

    // ---------------------------------------------------------------------------
    // CoverageReport builder test
    // ---------------------------------------------------------------------------

    @Test
    void should_coverageReport_builder() {
        List<CoverageService.PackageCoverage> packages = List.of(
                CoverageService.PackageCoverage.builder()
                        .packageName("com.example")
                        .lineCoverage(90.0)
                        .totalLines(100)
                        .coveredLines(90)
                        .build()
        );

        CoverageService.CoverageReport report = CoverageService.CoverageReport.builder()
                .lineCoverage(85.0)
                .branchCoverage(70.0)
                .methodCoverage(90.0)
                .statementCoverage(82.0)
                .totalLines(200)
                .coveredLines(170)
                .totalBranches(50)
                .coveredBranches(35)
                .totalMethods(40)
                .coveredMethods(36)
                .reportFormat("jacoco")
                .packages(packages)
                .build();

        assertEquals(85.0, report.getLineCoverage(), 0.01);
        assertEquals(70.0, report.getBranchCoverage(), 0.01);
        assertEquals(90.0, report.getMethodCoverage(), 0.01);
        assertEquals(82.0, report.getStatementCoverage(), 0.01);
        assertEquals(200, report.getTotalLines());
        assertEquals(170, report.getCoveredLines());
        assertEquals(50, report.getTotalBranches());
        assertEquals(35, report.getCoveredBranches());
        assertEquals(40, report.getTotalMethods());
        assertEquals(36, report.getCoveredMethods());
        assertEquals("jacoco", report.getReportFormat());
        assertEquals(1, report.getPackages().size());
        assertEquals("com.example", report.getPackages().get(0).getPackageName());
    }

    @Test
    void should_packageCoverage_builder() {
        CoverageService.PackageCoverage pkg = CoverageService.PackageCoverage.builder()
                .packageName("com.squadron.test")
                .lineCoverage(92.5)
                .totalLines(80)
                .coveredLines(74)
                .build();

        assertEquals("com.squadron.test", pkg.getPackageName());
        assertEquals(92.5, pkg.getLineCoverage(), 0.01);
        assertEquals(80, pkg.getTotalLines());
        assertEquals(74, pkg.getCoveredLines());
    }
}
