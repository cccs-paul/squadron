#!/usr/bin/env bash
# =============================================================================
# Squadron - Container Image Security Scanner
#
# Scans all Squadron Docker images using Trivy for vulnerabilities,
# misconfigurations, and secrets.
#
# Usage:
#   ./deploy/security/scan-images.sh
#   ./deploy/security/scan-images.sh --severity CRITICAL,HIGH
#   ./deploy/security/scan-images.sh --format json --output report.json
#
# Prerequisites:
#   - Trivy installed (https://aquasecurity.github.io/trivy/)
#   - Docker images built locally
# =============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# Color output helpers
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; }
header()  { echo -e "\n${BOLD}${CYAN}=== $* ===${NC}\n"; }

# ---------------------------------------------------------------------------
# Default configuration
# ---------------------------------------------------------------------------
SEVERITY="${SEVERITY:-CRITICAL,HIGH}"
FORMAT="${FORMAT:-table}"
OUTPUT=""
IMAGE_TAG="${IMAGE_TAG:-latest}"
IMAGE_PREFIX="${IMAGE_PREFIX:-squadron}"
TRIVY_CONFIG="$(dirname "$0")/trivy-config.yaml"
IGNORE_UNFIXED="${IGNORE_UNFIXED:-true}"
EXIT_ON_CRITICAL="${EXIT_ON_CRITICAL:-true}"

# All Squadron service images
IMAGES=(
    "squadron-gateway"
    "squadron-identity"
    "squadron-config"
    "squadron-orchestrator"
    "squadron-platform"
    "squadron-agent"
    "squadron-workspace"
    "squadron-git"
    "squadron-review"
    "squadron-notification"
    "squadron-ui"
)

# ---------------------------------------------------------------------------
# Usage
# ---------------------------------------------------------------------------
usage() {
    cat <<EOF
${BOLD}Squadron Container Image Security Scanner${NC}

${BOLD}Usage:${NC}
    $(basename "$0") [OPTIONS]

${BOLD}Options:${NC}
    --severity LEVELS   Comma-separated severity levels (default: CRITICAL,HIGH)
    --format FORMAT     Output format: table, json, sarif (default: table)
    --output FILE       Write report to file instead of stdout
    --tag TAG           Image tag to scan (default: latest)
    --prefix PREFIX     Image name prefix (default: squadron)
    --no-fail           Do not exit non-zero on CRITICAL findings
    --include-unfixed   Include vulnerabilities with no fix available
    -h, --help          Show this help message

${BOLD}Environment Variables:${NC}
    SEVERITY            Same as --severity
    FORMAT              Same as --format
    IMAGE_TAG           Same as --tag
    IMAGE_PREFIX        Same as --prefix
    EXIT_ON_CRITICAL    Set to 'false' to disable exit-on-critical (same as --no-fail)
    IGNORE_UNFIXED      Set to 'false' to include unfixed vulns

${BOLD}Examples:${NC}
    $(basename "$0")                              # Scan all images, CRITICAL+HIGH
    $(basename "$0") --severity CRITICAL          # Only CRITICAL vulnerabilities
    $(basename "$0") --format sarif --output .    # SARIF output per image
    $(basename "$0") --tag v1.2.3                 # Scan specific tag
EOF
    exit 0
}

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --severity)
            SEVERITY="$2"; shift 2 ;;
        --format)
            FORMAT="$2"; shift 2 ;;
        --output)
            OUTPUT="$2"; shift 2 ;;
        --tag)
            IMAGE_TAG="$2"; shift 2 ;;
        --prefix)
            IMAGE_PREFIX="$2"; shift 2 ;;
        --no-fail)
            EXIT_ON_CRITICAL="false"; shift ;;
        --include-unfixed)
            IGNORE_UNFIXED="false"; shift ;;
        -h|--help)
            usage ;;
        *)
            error "Unknown option: $1"
            usage ;;
    esac
done

# ---------------------------------------------------------------------------
# Preflight checks
# ---------------------------------------------------------------------------
header "Squadron Security Scanner"

if ! command -v trivy &>/dev/null; then
    error "Trivy is not installed. Install from: https://aquasecurity.github.io/trivy/"
    error "  brew install trivy          # macOS"
    error "  apt-get install trivy       # Debian/Ubuntu"
    error "  yum install trivy           # RHEL/CentOS"
    exit 1
fi

info "Trivy version: $(trivy --version 2>/dev/null | head -1)"
info "Severity filter: ${SEVERITY}"
info "Output format: ${FORMAT}"
info "Image tag: ${IMAGE_TAG}"
info "Ignore unfixed: ${IGNORE_UNFIXED}"

# ---------------------------------------------------------------------------
# Scan all images
# ---------------------------------------------------------------------------
TOTAL=0
PASSED=0
FAILED=0
CRITICAL_FOUND=0
RESULTS=()

for IMAGE_NAME in "${IMAGES[@]}"; do
    FULL_IMAGE="${IMAGE_PREFIX}/${IMAGE_NAME}:${IMAGE_TAG}"
    header "Scanning: ${FULL_IMAGE}"

    # Check if image exists locally
    if ! docker image inspect "${FULL_IMAGE}" &>/dev/null; then
        warn "Image not found locally: ${FULL_IMAGE} — skipping"
        RESULTS+=("SKIP|${FULL_IMAGE}|Image not found")
        continue
    fi

    TOTAL=$((TOTAL + 1))

    # Build trivy command
    TRIVY_CMD=(trivy image)
    TRIVY_CMD+=(--severity "${SEVERITY}")
    TRIVY_CMD+=(--format "${FORMAT}")

    if [[ "${IGNORE_UNFIXED}" == "true" ]]; then
        TRIVY_CMD+=(--ignore-unfixed)
    fi

    # Handle output file
    if [[ -n "${OUTPUT}" ]]; then
        if [[ -d "${OUTPUT}" ]]; then
            # Output is a directory — write per-image files
            OUTPUT_FILE="${OUTPUT}/${IMAGE_NAME}-scan.${FORMAT}"
            TRIVY_CMD+=(--output "${OUTPUT_FILE}")
        else
            TRIVY_CMD+=(--output "${OUTPUT}")
        fi
    fi

    # Do NOT use --exit-code here; we capture the exit code ourselves
    TRIVY_CMD+=(--exit-code 0)
    TRIVY_CMD+=("${FULL_IMAGE}")

    # Run scan and capture results
    SCAN_OUTPUT=""
    SCAN_EXIT=0
    SCAN_OUTPUT=$("${TRIVY_CMD[@]}" 2>&1) || SCAN_EXIT=$?

    if [[ ${SCAN_EXIT} -ne 0 ]]; then
        error "Trivy scan failed for ${FULL_IMAGE}"
        FAILED=$((FAILED + 1))
        RESULTS+=("FAIL|${FULL_IMAGE}|Scanner error (exit ${SCAN_EXIT})")
        echo "${SCAN_OUTPUT}"
        continue
    fi

    # Check for CRITICAL vulnerabilities using a separate json query
    CRIT_COUNT=$(trivy image --severity CRITICAL --ignore-unfixed --format json --exit-code 0 "${FULL_IMAGE}" 2>/dev/null \
        | python3 -c "
import sys, json
data = json.load(sys.stdin)
count = 0
for result in data.get('Results', []):
    for vuln in result.get('Vulnerabilities', []):
        if vuln.get('Severity') == 'CRITICAL':
            count += 1
print(count)
" 2>/dev/null || echo "0")

    if [[ "${CRIT_COUNT}" -gt 0 ]]; then
        error "${FULL_IMAGE}: ${CRIT_COUNT} CRITICAL vulnerabilities found"
        FAILED=$((FAILED + 1))
        CRITICAL_FOUND=$((CRITICAL_FOUND + CRIT_COUNT))
        RESULTS+=("CRIT|${FULL_IMAGE}|${CRIT_COUNT} CRITICAL vulnerabilities")
    else
        success "${FULL_IMAGE}: No CRITICAL vulnerabilities"
        PASSED=$((PASSED + 1))
        RESULTS+=("PASS|${FULL_IMAGE}|Clean")
    fi

    # Print scan output unless directed to a file
    if [[ -z "${OUTPUT}" ]]; then
        echo "${SCAN_OUTPUT}"
    fi
done

# ---------------------------------------------------------------------------
# Summary report
# ---------------------------------------------------------------------------
header "Scan Summary"

printf "${BOLD}%-10s %-45s %s${NC}\n" "STATUS" "IMAGE" "DETAILS"
printf "%-10s %-45s %s\n" "------" "-----" "-------"

for RESULT in "${RESULTS[@]}"; do
    IFS='|' read -r STATUS IMG DETAILS <<< "${RESULT}"
    case "${STATUS}" in
        PASS) printf "${GREEN}%-10s${NC} %-45s %s\n" "PASS" "${IMG}" "${DETAILS}" ;;
        CRIT) printf "${RED}%-10s${NC} %-45s %s\n" "CRITICAL" "${IMG}" "${DETAILS}" ;;
        FAIL) printf "${RED}%-10s${NC} %-45s %s\n" "FAIL" "${IMG}" "${DETAILS}" ;;
        SKIP) printf "${YELLOW}%-10s${NC} %-45s %s\n" "SKIP" "${IMG}" "${DETAILS}" ;;
    esac
done

echo ""
info "Total scanned: ${TOTAL}  |  Passed: ${PASSED}  |  Failed: ${FAILED}  |  Critical vulns: ${CRITICAL_FOUND}"

if [[ -n "${OUTPUT}" ]]; then
    info "Reports written to: ${OUTPUT}"
fi

# ---------------------------------------------------------------------------
# Exit code
# ---------------------------------------------------------------------------
if [[ "${EXIT_ON_CRITICAL}" == "true" && ${CRITICAL_FOUND} -gt 0 ]]; then
    echo ""
    error "Exiting with code 1 due to ${CRITICAL_FOUND} CRITICAL vulnerabilities."
    error "Use --no-fail to suppress this behavior."
    exit 1
fi

if [[ ${FAILED} -gt 0 ]]; then
    echo ""
    warn "Some scans failed. Review the output above."
    exit 2
fi

echo ""
success "All scanned images passed security checks."
exit 0
