package com.squadron.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.tool.builtin.ExecResultDto;
import com.squadron.agent.tool.builtin.WorkspaceClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parses coverage reports from workspace containers. Supports JaCoCo (Java),
 * Istanbul (JavaScript/TypeScript), and coverage.py (Python) report formats.
 */
@Service
public class CoverageService {

    private static final Logger log = LoggerFactory.getLogger(CoverageService.class);

    private final WorkspaceClient workspaceClient;
    private final ObjectMapper objectMapper;

    public CoverageService(WorkspaceClient workspaceClient, ObjectMapper objectMapper) {
        this.workspaceClient = workspaceClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Detects the project type by examining files present in the workspace root.
     *
     * @param workspaceId the workspace container ID
     * @return "java", "javascript", "python", or "unknown"
     */
    public String detectProjectType(UUID workspaceId) {
        try {
            ExecResultDto result = workspaceClient.exec(workspaceId, "ls", "/workspace");
            if (result.getExitCode() != 0) {
                log.warn("Failed to list workspace directory for {}: {}", workspaceId, result.getStderr());
                return "unknown";
            }

            String output = result.getStdout();
            if (output.contains("pom.xml") || output.contains("build.gradle")) {
                return "java";
            }
            if (output.contains("package.json")) {
                return "javascript";
            }
            if (output.contains("requirements.txt") || output.contains("setup.py")
                    || output.contains("pyproject.toml")) {
                return "python";
            }

            return "unknown";
        } catch (Exception e) {
            log.error("Error detecting project type for workspace {}", workspaceId, e);
            return "unknown";
        }
    }

    /**
     * Collects coverage data by detecting the project type and dispatching to
     * the appropriate coverage parser.
     *
     * @param workspaceId the workspace container ID
     * @return the parsed coverage report, or an empty report if detection fails
     */
    public CoverageReport collectCoverage(UUID workspaceId) {
        String projectType = detectProjectType(workspaceId);
        log.info("Detected project type '{}' for workspace {}", projectType, workspaceId);

        return switch (projectType) {
            case "java" -> collectJacocoCoverage(workspaceId);
            case "javascript" -> collectIstanbulCoverage(workspaceId);
            case "python" -> collectPythonCoverage(workspaceId);
            default -> {
                log.warn("Unknown project type for workspace {}, returning empty coverage report", workspaceId);
                yield CoverageReport.builder()
                        .reportFormat("unknown")
                        .build();
            }
        };
    }

    /**
     * Parses JaCoCo CSV coverage report from the workspace.
     * Expected CSV format: GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,
     * BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,COMPLEXITY_MISSED,
     * COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED
     *
     * @param workspaceId the workspace container ID
     * @return parsed coverage report
     */
    public CoverageReport collectJacocoCoverage(UUID workspaceId) {
        try {
            ExecResultDto result = workspaceClient.exec(workspaceId,
                    "cat", "/workspace/target/site/jacoco/jacoco.csv");

            if (result.getExitCode() != 0) {
                log.warn("JaCoCo CSV not found in workspace {}: {}", workspaceId, result.getStderr());
                return CoverageReport.builder().reportFormat("jacoco").build();
            }

            String csv = result.getStdout();
            BufferedReader reader = new BufferedReader(new StringReader(csv));

            // Skip header line
            String header = reader.readLine();
            if (header == null) {
                return CoverageReport.builder().reportFormat("jacoco").build();
            }

            int totalLineMissed = 0, totalLineCovered = 0;
            int totalBranchMissed = 0, totalBranchCovered = 0;
            int totalMethodMissed = 0, totalMethodCovered = 0;
            List<PackageCoverage> packages = new ArrayList<>();
            String currentPackage = null;
            int pkgLineMissed = 0, pkgLineCovered = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(",");
                if (cols.length < 13) {
                    continue;
                }

                String packageName = cols[1];
                int lineMissed = parseIntSafe(cols[7]);
                int lineCovered = parseIntSafe(cols[8]);
                int branchMissed = parseIntSafe(cols[5]);
                int branchCovered = parseIntSafe(cols[6]);
                int methodMissed = parseIntSafe(cols[11]);
                int methodCovered = parseIntSafe(cols[12]);

                totalLineMissed += lineMissed;
                totalLineCovered += lineCovered;
                totalBranchMissed += branchMissed;
                totalBranchCovered += branchCovered;
                totalMethodMissed += methodMissed;
                totalMethodCovered += methodCovered;

                // Aggregate per package
                if (!packageName.equals(currentPackage)) {
                    if (currentPackage != null) {
                        packages.add(buildPackageCoverage(currentPackage, pkgLineMissed, pkgLineCovered));
                    }
                    currentPackage = packageName;
                    pkgLineMissed = 0;
                    pkgLineCovered = 0;
                }
                pkgLineMissed += lineMissed;
                pkgLineCovered += lineCovered;
            }

            // Add the last package
            if (currentPackage != null) {
                packages.add(buildPackageCoverage(currentPackage, pkgLineMissed, pkgLineCovered));
            }

            int totalLines = totalLineMissed + totalLineCovered;
            int totalBranches = totalBranchMissed + totalBranchCovered;
            int totalMethods = totalMethodMissed + totalMethodCovered;

            return CoverageReport.builder()
                    .lineCoverage(percentage(totalLineCovered, totalLines))
                    .branchCoverage(percentage(totalBranchCovered, totalBranches))
                    .methodCoverage(percentage(totalMethodCovered, totalMethods))
                    .statementCoverage(0.0) // JaCoCo CSV does not separate statement coverage
                    .totalLines(totalLines)
                    .coveredLines(totalLineCovered)
                    .totalBranches(totalBranches)
                    .coveredBranches(totalBranchCovered)
                    .totalMethods(totalMethods)
                    .coveredMethods(totalMethodCovered)
                    .reportFormat("jacoco")
                    .packages(packages)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse JaCoCo coverage for workspace {}", workspaceId, e);
            return CoverageReport.builder().reportFormat("jacoco").build();
        }
    }

    /**
     * Parses Istanbul (nyc) JSON coverage summary from the workspace.
     * Expected JSON structure: { "total": { "lines": { "pct": N }, "statements": { "pct": N },
     * "functions": { "pct": N }, "branches": { "pct": N } } }
     *
     * @param workspaceId the workspace container ID
     * @return parsed coverage report
     */
    public CoverageReport collectIstanbulCoverage(UUID workspaceId) {
        try {
            ExecResultDto result = workspaceClient.exec(workspaceId,
                    "cat", "/workspace/coverage/coverage-summary.json");

            if (result.getExitCode() != 0) {
                log.warn("Istanbul coverage summary not found in workspace {}: {}",
                        workspaceId, result.getStderr());
                return CoverageReport.builder().reportFormat("istanbul").build();
            }

            Map<String, Object> json = objectMapper.readValue(
                    result.getStdout(), new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            Map<String, Object> total = (Map<String, Object>) json.get("total");
            if (total == null) {
                log.warn("No 'total' key in Istanbul coverage summary for workspace {}", workspaceId);
                return CoverageReport.builder().reportFormat("istanbul").build();
            }

            double linePct = extractPct(total, "lines");
            double stmtPct = extractPct(total, "statements");
            double funcPct = extractPct(total, "functions");
            double branchPct = extractPct(total, "branches");

            int totalLines = extractIntField(total, "lines", "total");
            int coveredLines = extractIntField(total, "lines", "covered");
            int totalBranches = extractIntField(total, "branches", "total");
            int coveredBranches = extractIntField(total, "branches", "covered");
            int totalMethods = extractIntField(total, "functions", "total");
            int coveredMethods = extractIntField(total, "functions", "covered");

            return CoverageReport.builder()
                    .lineCoverage(linePct)
                    .branchCoverage(branchPct)
                    .methodCoverage(funcPct)
                    .statementCoverage(stmtPct)
                    .totalLines(totalLines)
                    .coveredLines(coveredLines)
                    .totalBranches(totalBranches)
                    .coveredBranches(coveredBranches)
                    .totalMethods(totalMethods)
                    .coveredMethods(coveredMethods)
                    .reportFormat("istanbul")
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Istanbul coverage for workspace {}", workspaceId, e);
            return CoverageReport.builder().reportFormat("istanbul").build();
        }
    }

    /**
     * Parses Python coverage.py JSON report from the workspace.
     * Tries reading /workspace/coverage.json first.
     *
     * @param workspaceId the workspace container ID
     * @return parsed coverage report
     */
    public CoverageReport collectPythonCoverage(UUID workspaceId) {
        try {
            ExecResultDto result = workspaceClient.exec(workspaceId,
                    "cat", "/workspace/coverage.json");

            if (result.getExitCode() != 0) {
                // Fallback: try generating the report on the fly
                log.info("coverage.json not found, attempting to generate for workspace {}", workspaceId);
                result = workspaceClient.exec(workspaceId,
                        "bash", "-c", "cd /workspace && python -m coverage json -o /dev/stdout 2>/dev/null");
                if (result.getExitCode() != 0) {
                    log.warn("Python coverage report not available for workspace {}: {}",
                            workspaceId, result.getStderr());
                    return CoverageReport.builder().reportFormat("coverage.py").build();
                }
            }

            Map<String, Object> json = objectMapper.readValue(
                    result.getStdout(), new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            Map<String, Object> totals = (Map<String, Object>) json.get("totals");
            if (totals == null) {
                log.warn("No 'totals' key in Python coverage report for workspace {}", workspaceId);
                return CoverageReport.builder().reportFormat("coverage.py").build();
            }

            double linePct = totals.get("percent_covered") instanceof Number n
                    ? n.doubleValue() : 0.0;
            int coveredLines = totals.get("covered_lines") instanceof Number n
                    ? n.intValue() : 0;
            int missingLines = totals.get("missing_lines") instanceof Number n
                    ? n.intValue() : 0;
            int totalLines = coveredLines + missingLines;
            int coveredBranches = totals.get("covered_branches") instanceof Number n
                    ? n.intValue() : 0;
            int missingBranches = totals.get("missing_branches") instanceof Number n
                    ? n.intValue() : 0;
            int totalBranches = coveredBranches + missingBranches;

            return CoverageReport.builder()
                    .lineCoverage(linePct)
                    .branchCoverage(percentage(coveredBranches, totalBranches))
                    .methodCoverage(0.0) // coverage.py does not track method-level coverage
                    .statementCoverage(linePct) // coverage.py line == statement
                    .totalLines(totalLines)
                    .coveredLines(coveredLines)
                    .totalBranches(totalBranches)
                    .coveredBranches(coveredBranches)
                    .totalMethods(0)
                    .coveredMethods(0)
                    .reportFormat("coverage.py")
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Python coverage for workspace {}", workspaceId, e);
            return CoverageReport.builder().reportFormat("coverage.py").build();
        }
    }

    // ---- Helper methods ----

    private static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double percentage(int covered, int total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round((double) covered / total * 10000.0) / 100.0;
    }

    private PackageCoverage buildPackageCoverage(String packageName, int missed, int covered) {
        int total = missed + covered;
        return PackageCoverage.builder()
                .packageName(packageName)
                .lineCoverage(percentage(covered, total))
                .totalLines(total)
                .coveredLines(covered)
                .build();
    }

    @SuppressWarnings("unchecked")
    private double extractPct(Map<String, Object> total, String category) {
        Object catObj = total.get(category);
        if (catObj instanceof Map<?, ?> catMap) {
            Object pct = catMap.get("pct");
            if (pct instanceof Number n) {
                return n.doubleValue();
            }
        }
        return 0.0;
    }

    @SuppressWarnings("unchecked")
    private int extractIntField(Map<String, Object> total, String category, String field) {
        Object catObj = total.get(category);
        if (catObj instanceof Map<?, ?> catMap) {
            Object value = catMap.get(field);
            if (value instanceof Number n) {
                return n.intValue();
            }
        }
        return 0;
    }

    // ---- Inner classes ----

    /**
     * Aggregated coverage report across all source files.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoverageReport {

        /** Line coverage percentage (0-100) */
        private double lineCoverage;

        /** Branch coverage percentage (0-100) */
        private double branchCoverage;

        /** Method/function coverage percentage (0-100) */
        private double methodCoverage;

        /** Statement coverage percentage (0-100) */
        private double statementCoverage;

        private int totalLines;
        private int coveredLines;
        private int totalBranches;
        private int coveredBranches;
        private int totalMethods;
        private int coveredMethods;

        /** Report format identifier: "jacoco", "istanbul", "coverage.py" */
        private String reportFormat;

        /** Per-package coverage breakdown (may be null) */
        private List<PackageCoverage> packages;
    }

    /**
     * Coverage metrics for a single package or directory.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageCoverage {

        private String packageName;
        private double lineCoverage;
        private int totalLines;
        private int coveredLines;
    }
}
