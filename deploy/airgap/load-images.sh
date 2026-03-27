#!/usr/bin/env bash
# =============================================================================
# Squadron - Air-Gap Image Loader
#
# Loads Squadron Docker images from a bundle archive and optionally pushes
# them to an internal container registry.
#
# Usage:
#   ./deploy/airgap/load-images.sh
#   ./deploy/airgap/load-images.sh --archive /path/to/squadron-images.tar.gz
#   PUSH_TO_REGISTRY=true ./deploy/airgap/load-images.sh
#
# Prerequisites:
#   - Docker installed and running
#   - squadron-images.tar.gz in the current directory (or specify --archive)
#   - If pushing to registry: registry must be accessible
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
NC='\033[0m'
BOLD='\033[1m'

info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; }
header()  { echo -e "\n${BOLD}${CYAN}=== $* ===${NC}\n"; }

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
ARCHIVE="${ARCHIVE:-squadron-images.tar.gz}"
PUSH_TO_REGISTRY="${PUSH_TO_REGISTRY:-false}"
REGISTRY="${REGISTRY:-registry.internal:5000}"
VERIFY_ONLY="${VERIFY_ONLY:-false}"

# Expected Squadron service images
EXPECTED_SERVICES=(
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
${BOLD}Squadron Air-Gap Image Loader${NC}

${BOLD}Usage:${NC}
    $(basename "$0") [OPTIONS]

${BOLD}Options:${NC}
    --archive FILE        Path to squadron-images.tar.gz (default: ./squadron-images.tar.gz)
    --push                Push loaded images to internal registry
    --registry REGISTRY   Internal registry address (default: registry.internal:5000)
    --verify-only         Only verify images are present (do not load or push)
    -h, --help            Show this help message

${BOLD}Environment Variables:${NC}
    ARCHIVE               Same as --archive
    PUSH_TO_REGISTRY      Set to 'true' to push (same as --push)
    REGISTRY              Same as --registry
    VERIFY_ONLY           Set to 'true' for verify-only mode

${BOLD}Examples:${NC}
    $(basename "$0")                                           # Load images from default archive
    $(basename "$0") --archive /mnt/usb/squadron-images.tar.gz # Load from USB drive
    $(basename "$0") --push --registry myregistry:5000         # Load and push to registry
    $(basename "$0") --verify-only                             # Just verify images exist
EOF
    exit 0
}

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --archive)
            ARCHIVE="$2"; shift 2 ;;
        --push)
            PUSH_TO_REGISTRY="true"; shift ;;
        --registry)
            REGISTRY="$2"; shift 2 ;;
        --verify-only)
            VERIFY_ONLY="true"; shift ;;
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
header "Squadron Air-Gap Image Loader"

if ! command -v docker &>/dev/null; then
    error "Docker is not installed or not in PATH."
    exit 1
fi

if ! docker info &>/dev/null; then
    error "Docker daemon is not running."
    exit 1
fi

info "Archive:          ${ARCHIVE}"
info "Push to registry: ${PUSH_TO_REGISTRY}"
info "Registry:         ${REGISTRY}"
info "Verify only:      ${VERIFY_ONLY}"

# ---------------------------------------------------------------------------
# Step 1: Load images from archive
# ---------------------------------------------------------------------------
if [[ "${VERIFY_ONLY}" != "true" ]]; then
    header "Loading Images from Archive"

    if [[ ! -f "${ARCHIVE}" ]]; then
        error "Archive not found: ${ARCHIVE}"
        error "Provide the correct path with --archive or place squadron-images.tar.gz in the current directory."
        exit 1
    fi

    ARCHIVE_SIZE=$(du -sh "${ARCHIVE}" | cut -f1)
    info "Archive size: ${ARCHIVE_SIZE}"
    info "Loading images into Docker (this may take several minutes)..."

    # Decompress and load
    if [[ "${ARCHIVE}" == *.gz ]]; then
        gunzip -c "${ARCHIVE}" | docker load
    else
        docker load -i "${ARCHIVE}"
    fi

    success "Images loaded successfully."
fi

# ---------------------------------------------------------------------------
# Step 2: Verify all expected images are present
# ---------------------------------------------------------------------------
header "Verifying Images"

LOADED_IMAGES=$(docker images --format '{{.Repository}}:{{.Tag}}' | sort)
MISSING=0
FOUND=0

for SERVICE in "${EXPECTED_SERVICES[@]}"; do
    # Check for the image with any registry prefix
    MATCH=$(echo "${LOADED_IMAGES}" | grep -E "(^|/)squadron/${SERVICE}:" || true)

    if [[ -n "${MATCH}" ]]; then
        success "Found: ${MATCH}"
        FOUND=$((FOUND + 1))
    else
        # Also check without the squadron/ prefix
        MATCH=$(echo "${LOADED_IMAGES}" | grep -E "(^|/)${SERVICE}:" || true)
        if [[ -n "${MATCH}" ]]; then
            success "Found: ${MATCH}"
            FOUND=$((FOUND + 1))
        else
            error "Missing: ${SERVICE}"
            MISSING=$((MISSING + 1))
        fi
    fi
done

echo ""
info "Found: ${FOUND}/${#EXPECTED_SERVICES[@]} expected images"

if [[ ${MISSING} -gt 0 ]]; then
    warn "${MISSING} expected images are missing."
    warn "The bundle may be incomplete or images may use different names."
fi

# ---------------------------------------------------------------------------
# Step 3: Push to internal registry (optional)
# ---------------------------------------------------------------------------
if [[ "${PUSH_TO_REGISTRY}" == "true" ]]; then
    header "Pushing Images to Registry: ${REGISTRY}"

    # Verify registry is accessible
    info "Testing registry connectivity..."
    if ! docker pull "${REGISTRY}/test-connectivity" &>/dev/null 2>&1; then
        # A pull failure is expected (image doesn't exist), but a connection
        # refused error means the registry is unreachable. We check by trying
        # to reach the /v2/ endpoint with curl if available, otherwise we
        # proceed optimistically.
        if command -v curl &>/dev/null; then
            if ! curl -sf "http://${REGISTRY}/v2/" &>/dev/null && \
               ! curl -sf "https://${REGISTRY}/v2/" &>/dev/null; then
                warn "Registry at ${REGISTRY} may not be reachable."
                warn "Attempting to push anyway..."
            fi
        fi
    fi

    PUSH_SUCCESS=0
    PUSH_FAIL=0

    # Find all squadron images and push them
    SQUADRON_IMAGES=$(docker images --format '{{.Repository}}:{{.Tag}}' \
        | grep "^${REGISTRY}" | sort)

    if [[ -z "${SQUADRON_IMAGES}" ]]; then
        warn "No images found tagged for ${REGISTRY}."
        warn "Images may need to be re-tagged. Attempting to tag and push..."

        for SERVICE in "${EXPECTED_SERVICES[@]}"; do
            # Find the image regardless of its current tag
            SOURCE=$(docker images --format '{{.Repository}}:{{.Tag}}' \
                | grep -E "(squadron/)?${SERVICE}:" | head -1 || true)

            if [[ -z "${SOURCE}" ]]; then
                warn "Cannot find image for ${SERVICE} — skipping"
                PUSH_FAIL=$((PUSH_FAIL + 1))
                continue
            fi

            TARGET="${REGISTRY}/squadron/${SERVICE}:$(echo "${SOURCE}" | cut -d: -f2)"
            info "Tagging ${SOURCE} -> ${TARGET}"
            docker tag "${SOURCE}" "${TARGET}"

            info "Pushing ${TARGET}..."
            if docker push "${TARGET}"; then
                success "Pushed: ${TARGET}"
                PUSH_SUCCESS=$((PUSH_SUCCESS + 1))
            else
                error "Failed to push: ${TARGET}"
                PUSH_FAIL=$((PUSH_FAIL + 1))
            fi
        done
    else
        while IFS= read -r IMAGE; do
            info "Pushing ${IMAGE}..."
            if docker push "${IMAGE}"; then
                success "Pushed: ${IMAGE}"
                PUSH_SUCCESS=$((PUSH_SUCCESS + 1))
            else
                error "Failed to push: ${IMAGE}"
                PUSH_FAIL=$((PUSH_FAIL + 1))
            fi
        done <<< "${SQUADRON_IMAGES}"
    fi

    echo ""
    info "Push complete: ${PUSH_SUCCESS} succeeded, ${PUSH_FAIL} failed"

    if [[ ${PUSH_FAIL} -gt 0 ]]; then
        warn "Some images failed to push. Check registry connectivity and authentication."
    fi
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
header "Summary"

if [[ "${VERIFY_ONLY}" == "true" ]]; then
    info "Verification complete."
else
    info "Images loaded from: ${ARCHIVE}"
fi

info "Images found: ${FOUND}/${#EXPECTED_SERVICES[@]}"

if [[ "${PUSH_TO_REGISTRY}" == "true" ]]; then
    info "Push results: ${PUSH_SUCCESS:-0} succeeded, ${PUSH_FAIL:-0} failed"
fi

if [[ ${MISSING} -eq 0 ]]; then
    echo ""
    success "All expected Squadron images are present."
    echo ""
    info "Next steps:"
    if [[ "${PUSH_TO_REGISTRY}" != "true" ]]; then
        info "  1. Push images to registry: $(basename "$0") --push --registry ${REGISTRY}"
    fi
    info "  2. Deploy with Helm: helm install squadron ./squadron-chart -f values-airgap.yaml"
    exit 0
else
    echo ""
    warn "Some images are missing. Review the output above."
    exit 1
fi
