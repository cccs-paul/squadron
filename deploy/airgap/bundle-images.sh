#!/usr/bin/env bash
# =============================================================================
# Squadron - Air-Gap Image Bundle Creator
#
# Builds all Squadron Docker images, tags them for an internal registry,
# and saves them to a single compressed tar archive for air-gap deployment.
#
# Usage:
#   ./deploy/airgap/bundle-images.sh
#   REGISTRY=my-registry:5000 ./deploy/airgap/bundle-images.sh
#   IMAGE_TAG=v1.2.3 ./deploy/airgap/bundle-images.sh
#
# Output:
#   squadron-images.tar.gz  (all images in a single archive)
#
# Prerequisites:
#   - Docker installed and running
#   - Maven 3.9+ and JDK 21 (for building Java services)
#   - Node.js 20+ (for building the Angular UI)
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
REGISTRY="${REGISTRY:-registry.internal:5000}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
OUTPUT_DIR="${OUTPUT_DIR:-.}"
OUTPUT_FILE="${OUTPUT_DIR}/squadron-images.tar.gz"
BUILD_IMAGES="${BUILD_IMAGES:-true}"
INCLUDE_INFRA="${INCLUDE_INFRA:-false}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Squadron service images
SERVICE_IMAGES=(
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

# Infrastructure images (optional, included if INCLUDE_INFRA=true)
INFRA_IMAGES=(
    "postgres:17"
    "redis:7-alpine"
    "nats:2-alpine"
    "quay.io/keycloak/keycloak:26.0"
    "ollama/ollama:latest"
)

# ---------------------------------------------------------------------------
# Usage
# ---------------------------------------------------------------------------
usage() {
    cat <<EOF
${BOLD}Squadron Air-Gap Image Bundle Creator${NC}

${BOLD}Usage:${NC}
    $(basename "$0") [OPTIONS]

${BOLD}Options:${NC}
    --registry REGISTRY   Target internal registry (default: registry.internal:5000)
    --tag TAG              Image tag (default: latest)
    --output DIR           Output directory for the tar.gz (default: current directory)
    --skip-build           Skip building images (use existing local images)
    --include-infra        Include infrastructure images (PostgreSQL, Redis, NATS, etc.)
    -h, --help             Show this help message

${BOLD}Environment Variables:${NC}
    REGISTRY               Same as --registry
    IMAGE_TAG              Same as --tag
    OUTPUT_DIR             Same as --output
    BUILD_IMAGES           Set to 'false' to skip building (same as --skip-build)
    INCLUDE_INFRA          Set to 'true' to include infrastructure images

${BOLD}Examples:${NC}
    $(basename "$0")                                      # Build and bundle all
    $(basename "$0") --registry myregistry:5000            # Custom registry
    $(basename "$0") --tag v1.2.3 --include-infra         # Specific tag + infra
    $(basename "$0") --skip-build --output /tmp/bundles    # Bundle existing images
EOF
    exit 0
}

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --registry)
            REGISTRY="$2"; shift 2 ;;
        --tag)
            IMAGE_TAG="$2"; shift 2 ;;
        --output)
            OUTPUT_DIR="$2"; shift 2
            OUTPUT_FILE="${OUTPUT_DIR}/squadron-images.tar.gz" ;;
        --skip-build)
            BUILD_IMAGES="false"; shift ;;
        --include-infra)
            INCLUDE_INFRA="true"; shift ;;
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
header "Squadron Air-Gap Image Bundler"

if ! command -v docker &>/dev/null; then
    error "Docker is not installed or not in PATH."
    exit 1
fi

if ! docker info &>/dev/null; then
    error "Docker daemon is not running."
    exit 1
fi

info "Registry:       ${REGISTRY}"
info "Image tag:      ${IMAGE_TAG}"
info "Output:         ${OUTPUT_FILE}"
info "Build images:   ${BUILD_IMAGES}"
info "Include infra:  ${INCLUDE_INFRA}"

mkdir -p "${OUTPUT_DIR}"

# ---------------------------------------------------------------------------
# Step 1: Build images (optional)
# ---------------------------------------------------------------------------
if [[ "${BUILD_IMAGES}" == "true" ]]; then
    header "Building Squadron Docker Images"

    # Build Java services with Maven
    info "Building Java services with Maven..."
    (cd "${PROJECT_ROOT}" && mvn package -DskipTests --no-transfer-progress -q)
    success "Maven build complete"

    for IMAGE_NAME in "${SERVICE_IMAGES[@]}"; do
        MODULE_DIR="${PROJECT_ROOT}/${IMAGE_NAME}"
        DOCKERFILE="${MODULE_DIR}/Dockerfile"

        if [[ ! -f "${DOCKERFILE}" ]]; then
            warn "Dockerfile not found for ${IMAGE_NAME} at ${DOCKERFILE} — skipping build"
            continue
        fi

        info "Building ${IMAGE_NAME}..."
        docker build -t "squadron/${IMAGE_NAME}:${IMAGE_TAG}" \
            -f "${DOCKERFILE}" "${MODULE_DIR}/" \
            --quiet
        success "Built squadron/${IMAGE_NAME}:${IMAGE_TAG}"
    done
fi

# ---------------------------------------------------------------------------
# Step 2: Tag images for internal registry
# ---------------------------------------------------------------------------
header "Tagging Images for Internal Registry"

ALL_TAGGED_IMAGES=()

for IMAGE_NAME in "${SERVICE_IMAGES[@]}"; do
    SOURCE="squadron/${IMAGE_NAME}:${IMAGE_TAG}"
    TARGET="${REGISTRY}/squadron/${IMAGE_NAME}:${IMAGE_TAG}"

    if ! docker image inspect "${SOURCE}" &>/dev/null; then
        warn "Image not found: ${SOURCE} — skipping"
        continue
    fi

    docker tag "${SOURCE}" "${TARGET}"
    ALL_TAGGED_IMAGES+=("${TARGET}")
    info "Tagged: ${TARGET}"
done

# Tag infrastructure images if requested
if [[ "${INCLUDE_INFRA}" == "true" ]]; then
    header "Pulling and Tagging Infrastructure Images"

    for INFRA_IMAGE in "${INFRA_IMAGES[@]}"; do
        info "Pulling ${INFRA_IMAGE}..."
        docker pull "${INFRA_IMAGE}" --quiet || {
            warn "Failed to pull ${INFRA_IMAGE} — skipping"
            continue
        }

        # Derive a simple name for the internal registry tag
        SIMPLE_NAME=$(echo "${INFRA_IMAGE}" | sed 's|.*/||' | sed 's|:|-|')
        TARGET="${REGISTRY}/infra/${SIMPLE_NAME}:${IMAGE_TAG}"

        docker tag "${INFRA_IMAGE}" "${TARGET}"
        ALL_TAGGED_IMAGES+=("${TARGET}")
        info "Tagged: ${TARGET}"
    done
fi

if [[ ${#ALL_TAGGED_IMAGES[@]} -eq 0 ]]; then
    error "No images were tagged. Nothing to bundle."
    exit 1
fi

# ---------------------------------------------------------------------------
# Step 3: Save all images to a tar archive
# ---------------------------------------------------------------------------
header "Saving Images to Archive"

info "Saving ${#ALL_TAGGED_IMAGES[@]} images to ${OUTPUT_FILE}..."
info "This may take several minutes depending on image sizes."

# Save all images to a single tar, then compress
docker save "${ALL_TAGGED_IMAGES[@]}" | gzip > "${OUTPUT_FILE}"

success "Archive created: ${OUTPUT_FILE}"

# ---------------------------------------------------------------------------
# Step 4: Include Helm values override
# ---------------------------------------------------------------------------
AIRGAP_VALUES="${SCRIPT_DIR}/values-airgap.yaml"
if [[ -f "${AIRGAP_VALUES}" ]]; then
    # Create a complete bundle directory
    BUNDLE_DIR="${OUTPUT_DIR}/squadron-airgap-bundle"
    mkdir -p "${BUNDLE_DIR}"

    cp "${OUTPUT_FILE}" "${BUNDLE_DIR}/"
    cp "${AIRGAP_VALUES}" "${BUNDLE_DIR}/"

    # Include the load script
    LOAD_SCRIPT="${SCRIPT_DIR}/load-images.sh"
    if [[ -f "${LOAD_SCRIPT}" ]]; then
        cp "${LOAD_SCRIPT}" "${BUNDLE_DIR}/"
        chmod +x "${BUNDLE_DIR}/load-images.sh"
    fi

    # Create a manifest file
    cat > "${BUNDLE_DIR}/MANIFEST.txt" <<MANIFEST
Squadron Air-Gap Bundle
========================
Created: $(date -u '+%Y-%m-%d %H:%M:%S UTC')
Image Tag: ${IMAGE_TAG}
Registry: ${REGISTRY}
Include Infrastructure: ${INCLUDE_INFRA}

Images included:
$(printf '  - %s\n' "${ALL_TAGGED_IMAGES[@]}")

Files:
  squadron-images.tar.gz    - Docker images archive
  values-airgap.yaml        - Helm values override for air-gap deployment
  load-images.sh            - Script to load and push images
  MANIFEST.txt              - This file
MANIFEST

    info "Bundle directory created: ${BUNDLE_DIR}/"
fi

# ---------------------------------------------------------------------------
# Step 5: Print summary
# ---------------------------------------------------------------------------
header "Bundle Summary"

BUNDLE_SIZE=$(du -sh "${OUTPUT_FILE}" | cut -f1)

echo -e "${BOLD}Images bundled:${NC} ${#ALL_TAGGED_IMAGES[@]}"
echo -e "${BOLD}Archive size:${NC}  ${BUNDLE_SIZE}"
echo -e "${BOLD}Archive path:${NC}  ${OUTPUT_FILE}"
echo ""

echo -e "${BOLD}Bundled images:${NC}"
for IMG in "${ALL_TAGGED_IMAGES[@]}"; do
    echo "  - ${IMG}"
done

echo ""
success "Air-gap bundle created successfully."
echo ""
info "Next steps:"
info "  1. Transfer ${OUTPUT_FILE} to the air-gapped environment"
info "  2. Run: ./load-images.sh"
info "  3. Deploy with: helm install squadron ./squadron-chart -f values-airgap.yaml"
