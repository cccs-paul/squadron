# Security Scanning Runbook

This document covers how to run, interpret, and manage security scanning for the
Squadron project.

## Overview

Squadron uses three security scanning tools:

| Tool | Purpose | Scope |
|------|---------|-------|
| **Trivy** | Container image vulnerability scanning | Docker images |
| **OWASP Dependency-Check** | Java dependency CVE detection | Maven dependencies |
| **Gitleaks** | Secret/credential detection | Git history + working tree |

All three tools are integrated into the CI pipeline (`deploy/security/security-pipeline.yaml`)
and can be run locally as described below.

---

## 1. Trivy (Container Image Scanning)

### Installation

```bash
# macOS
brew install trivy

# Debian/Ubuntu
sudo apt-get install wget apt-transport-https gnupg lsb-release
wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | sudo apt-key add -
echo deb https://aquasecurity.github.io/trivy-repo/deb $(lsb_release -sc) main | \
  sudo tee -a /etc/apt/sources.list.d/trivy.list
sudo apt-get update && sudo apt-get install trivy

# RHEL/CentOS
sudo rpm -ivh https://github.com/aquasecurity/trivy/releases/latest/download/trivy_*_Linux-64bit.rpm
```

### Running Locally

#### Scan a single image

```bash
# Basic scan
trivy image squadron/squadron-gateway:latest

# Using Squadron's Trivy config
trivy image --config deploy/security/trivy-config.yaml squadron/squadron-gateway:latest

# Only CRITICAL vulnerabilities
trivy image --severity CRITICAL squadron/squadron-gateway:latest
```

#### Scan all Squadron images

```bash
# Scan all images with default settings (CRITICAL,HIGH)
./deploy/security/scan-images.sh

# Scan with custom severity
./deploy/security/scan-images.sh --severity CRITICAL,HIGH,MEDIUM

# Generate SARIF reports for GitHub
./deploy/security/scan-images.sh --format sarif --output ./reports/

# Scan a specific tag
./deploy/security/scan-images.sh --tag v1.2.3

# Don't fail on critical (for informational runs)
./deploy/security/scan-images.sh --no-fail
```

### Interpreting Trivy Results

Trivy reports vulnerabilities with the following severity levels:

| Severity | Action Required |
|----------|----------------|
| **CRITICAL** | Must fix before release. These are actively exploitable vulnerabilities. |
| **HIGH** | Fix within current sprint. Significant risk but may require specific conditions. |
| **MEDIUM** | Fix within next release cycle. Lower risk but still worth addressing. |
| **LOW** | Track and fix opportunistically. Minimal risk. |

Each finding includes:
- **CVE ID**: The Common Vulnerabilities and Exposures identifier
- **Package**: The affected package/library
- **Installed Version**: The version present in the image
- **Fixed Version**: The version that resolves the vulnerability (if available)
- **Severity**: CRITICAL/HIGH/MEDIUM/LOW

### Common Fixes

1. **Base image vulnerabilities**: Update the base image in the Dockerfile
   ```dockerfile
   # Update from
   FROM eclipse-temurin:21-jre-jammy
   # To the latest patched version
   FROM eclipse-temurin:21.0.5_11-jre-jammy
   ```

2. **OS package vulnerabilities**: Add package updates to the Dockerfile
   ```dockerfile
   RUN apt-get update && apt-get upgrade -y && rm -rf /var/lib/apt/lists/*
   ```

3. **Application dependency vulnerabilities**: Update the dependency in `pom.xml`

### Trivy Configuration

The project-level Trivy configuration is at `deploy/security/trivy-config.yaml`. Key settings:

- `severity`: Which severity levels to report
- `ignore-unfixed`: Skip vulnerabilities with no available fix
- `security-checks`: Check for `vuln` (CVEs), `config` (misconfigurations), `secret` (embedded secrets)

---

## 2. OWASP Dependency-Check

### Running Locally

```bash
# Full project scan
mvn org.owasp:dependency-check-maven:check \
  -DsuppressionFile=deploy/security/dependency-check-suppression.xml

# Single module
mvn org.owasp:dependency-check-maven:check \
  -DsuppressionFile=deploy/security/dependency-check-suppression.xml \
  -pl squadron-gateway

# Fail on CVSS >= 7 (HIGH and above)
mvn org.owasp:dependency-check-maven:check \
  -DsuppressionFile=deploy/security/dependency-check-suppression.xml \
  -DfailBuildOnCVSS=7

# Generate all report formats (HTML, JSON, SARIF)
mvn org.owasp:dependency-check-maven:check \
  -DsuppressionFile=deploy/security/dependency-check-suppression.xml \
  -Dformat=ALL
```

### Interpreting Results

Reports are generated at `target/dependency-check-report.html` in each module.

The report shows:
- **Dependency**: The Maven artifact (groupId:artifactId:version)
- **CPE**: Common Platform Enumeration identifier
- **CVE**: The vulnerability identifier
- **CVSS Score**: 0.0-10.0 severity score
- **Evidence**: How the dependency was matched to the CPE

CVSS Score mapping:
| Score | Severity | Action |
|-------|----------|--------|
| 9.0-10.0 | Critical | Immediate fix required |
| 7.0-8.9 | High | Fix within current sprint |
| 4.0-6.9 | Medium | Fix within release cycle |
| 0.1-3.9 | Low | Track and fix opportunistically |

### Suppressing False Positives

False positives are common with OWASP Dependency-Check. To suppress them:

1. Open `deploy/security/dependency-check-suppression.xml`

2. Add a suppression entry with a clear justification:
   ```xml
   <suppress>
       <notes><![CDATA[
           Justification: Explain why this is a false positive or why
           the vulnerability doesn't apply to Squadron's usage.
       ]]></notes>
       <gav regex="true">com\.example:affected-library.*</gav>
       <cve>CVE-2024-XXXXX</cve>
   </suppress>
   ```

3. Suppression types:
   - **By CVE**: Suppress a specific CVE for a specific dependency
     ```xml
     <cve>CVE-2024-XXXXX</cve>
     ```
   - **By CPE**: Suppress all CVEs matching a CPE (for misidentified dependencies)
     ```xml
     <cpe regex="true">cpe:/a:vendor:product.*</cpe>
     ```
   - **By vulnerability name pattern**: Suppress by regex on vulnerability name
     ```xml
     <vulnerabilityName regex="true">CVE-2024-.*</vulnerabilityName>
     ```

4. Always include a `<notes>` element explaining:
   - Why the CVE doesn't apply (e.g., "feature not used", "test-only dependency")
   - What mitigation is in place (e.g., "network-level restriction")
   - When to revisit (e.g., "remove when upgrading to Spring Boot 4.x")

### Updating the NVD Database

OWASP Dependency-Check downloads the NVD (National Vulnerability Database) on first run.
It can take 10-20 minutes. Subsequent runs use a cached copy.

To force a database update:
```bash
mvn org.owasp:dependency-check-maven:update-only
```

---

## 3. Gitleaks (Secret Scanning)

### Installation

```bash
# macOS
brew install gitleaks

# From source
go install github.com/gitleaks/gitleaks/v8@latest

# Docker
docker pull ghcr.io/gitleaks/gitleaks:latest
```

### Running Locally

```bash
# Scan working directory
gitleaks detect --source . --verbose

# Scan full git history
gitleaks detect --source . --log-opts="--all" --verbose

# Generate SARIF report
gitleaks detect --source . --report-format sarif --report-path gitleaks.sarif

# Generate JSON report
gitleaks detect --source . --report-format json --report-path gitleaks.json
```

### Handling Findings

If Gitleaks detects a secret:

1. **Verify it's a real secret** (not a test fixture or example value)
2. **If real**: Rotate the credential immediately, then remove it from the codebase
3. **If false positive**: Add it to `.gitleaksignore` in the repository root:
   ```
   # .gitleaksignore
   # Format: commit:file:rule:line
   abc123def:config/test.yaml:generic-api-key:15
   ```

---

## 4. CI Pipeline

The security pipeline runs automatically on:
- Every push to `main`
- Every pull request targeting `main`
- Weekly on Monday at 06:00 UTC (to catch new CVEs)

### Pipeline Jobs

| Job | Tool | Produces |
|-----|------|----------|
| `dependency-check` | OWASP Dependency-Check | SARIF + HTML report |
| `container-scan` | Trivy (matrix across all 11 images) | SARIF per image |
| `secret-scan` | Gitleaks | SARIF |

### Viewing Results

1. Go to the repository **Security** tab on GitHub
2. Click **Code scanning alerts**
3. Filter by tool: `owasp-dependency-check`, `trivy-*`, or `gitleaks`

### Pipeline Configuration

The pipeline is defined in `deploy/security/security-pipeline.yaml`. To activate it:

```bash
# Copy to GitHub Actions workflow directory
mkdir -p .github/workflows
cp deploy/security/security-pipeline.yaml .github/workflows/security.yaml
```

### Failure Policy

| Job | Failure Threshold | Behavior |
|-----|-------------------|----------|
| dependency-check | CVSS >= 9.0 | Fails the build |
| container-scan | Any CRITICAL or HIGH | Fails the build |
| secret-scan | Any detected secret | Fails the build |

---

## 5. Quick Reference

### Daily Development

```bash
# Quick vulnerability check on your changes
mvn org.owasp:dependency-check-maven:check \
  -DsuppressionFile=deploy/security/dependency-check-suppression.xml \
  -pl <your-module> -DfailBuildOnCVSS=7
```

### Before Release

```bash
# Full security audit
mvn org.owasp:dependency-check-maven:check \
  -DsuppressionFile=deploy/security/dependency-check-suppression.xml \
  -Dformat=ALL -DfailBuildOnCVSS=7

# Scan all container images
./deploy/security/scan-images.sh --severity CRITICAL,HIGH,MEDIUM

# Check for secrets
gitleaks detect --source . --log-opts="--all" --verbose
```

### Emergency CVE Response

1. Check if Squadron is affected:
   ```bash
   mvn org.owasp:dependency-check-maven:check \
     -DsuppressionFile=deploy/security/dependency-check-suppression.xml
   ```
2. If affected, update the dependency in `pom.xml`
3. Run tests: `mvn verify -q`
4. Rebuild and rescan images: `./deploy/security/scan-images.sh`
5. Deploy updated images
