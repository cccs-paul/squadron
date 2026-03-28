#!/usr/bin/env bash
# =============================================================================
# Squadron - Build and Start with Test LDAP
#
# Builds all Docker images and launches the application with its dependencies
# and a test OpenLDAP server (rroemhild/docker-test-openldap) using Docker
# Compose.
#
# The test LDAP provides a pre-populated directory under
# dc=planetexpress,dc=com with Futurama character accounts.
#
# Usage:
#   ./testldap-build-and-start.sh              # Build all images and start everything
#   ./testldap-build-and-start.sh --infra      # Start only infrastructure (no app services)
#   ./testldap-build-and-start.sh --skip-build # Start without rebuilding images
#   ./testldap-build-and-start.sh --skip-tests # Build without running tests
#   ./testldap-build-and-start.sh --clean      # Clean, build, and start fresh
#   ./testldap-build-and-start.sh --stop       # Stop all running services
#   ./testldap-build-and-start.sh --status     # Show status of all services
#   ./testldap-build-and-start.sh --logs       # Tail logs for all services
#   ./testldap-build-and-start.sh --help       # Show this help message
# =============================================================================
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="${SCRIPT_DIR}/deploy/docker"
COMPOSE_FILE="${COMPOSE_DIR}/docker-compose.yml"
COMPOSE_LDAP_FILE="${COMPOSE_DIR}/docker-compose-testldap.yml"

# Infrastructure services to start (order matters for health checks)
INFRA_SERVICES=(
    postgres
    redis
    nats
    keycloak
    mailpit
    pgbouncer
    ollama
    openldap-test
)

# Services that have Docker healthchecks defined and must become healthy
HEALTHCHECK_SERVICES=(
    postgres
    redis
    nats
    keycloak
    ollama
    openldap-test
)

# Backend service list
BACKEND_SERVICES=(
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
)

# =============================================================================
# Helper Functions
# =============================================================================

log_info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_success() { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()    { echo -e "\n${BOLD}${CYAN}==> $*${NC}"; }

usage() {
    cat <<EOF
${BOLD}Squadron - Build and Start with Test LDAP${NC}

Usage: $(basename "$0") [OPTIONS]

Options:
  --infra           Start only infrastructure services (PostgreSQL, Redis, NATS, etc.) + LDAP
  --skip-build      Start services without rebuilding Docker images
  --skip-tests      Build Maven artifacts without running tests
  --clean           Remove all containers/volumes and start fresh
  --stop            Stop all running services
  --status          Show status of all services
  --logs [SERVICE]  Tail logs (optionally for a specific service)
  --pull-model      Pull the default Ollama model after startup
  --no-gpu          Start Ollama without GPU support
  --help            Show this help message

Test LDAP Details:
  Image:            ghcr.io/rroemhild/docker-test-openldap:master
  Domain:           dc=planetexpress,dc=com
  Admin DN:         cn=admin,dc=planetexpress,dc=com
  Admin Password:   GoodNewsEveryone
  User Base DN:     ou=people,dc=planetexpress,dc=com
  LDAP Port:        10389
  LDAPS Port:       10636

  Test Users (uid / password):
    professor / professor    fry / fry          leela / leela
    bender / bender          zoidberg / zoidberg
    hermes / hermes          amy / amy

Environment Variables:
  OPENAI_API_KEY    OpenAI API key (default: sk-placeholder)
  OPENAI_BASE_URL   OpenAI base URL (default: https://api.openai.com)
  OPENAI_MODEL      OpenAI model name (default: gpt-4o)
  OLLAMA_MODEL      Ollama model name (default: qwen2.5-coder:7b)

Examples:
  $(basename "$0")                          # Full build and start with test LDAP
  $(basename "$0") --infra                  # Infrastructure + LDAP only (for local dev)
  $(basename "$0") --skip-build             # Quick restart without rebuilding
  $(basename "$0") --clean                  # Fresh start with clean data
  $(basename "$0") --stop                   # Shut everything down
  $(basename "$0") --logs openldap-test     # Watch LDAP service logs

EOF
    exit 0
}

check_prerequisites() {
    log_step "Checking prerequisites"

    local missing=()

    if ! command -v docker &>/dev/null; then
        missing+=("docker")
    fi

    if ! command -v docker compose &>/dev/null && ! command -v docker-compose &>/dev/null; then
        missing+=("docker-compose")
    fi

    if ! command -v java &>/dev/null; then
        missing+=("java (JDK 21)")
    fi

    if ! command -v mvn &>/dev/null; then
        missing+=("maven")
    fi

    if ! command -v node &>/dev/null; then
        missing+=("node (Node.js 22+)")
    fi

    if [ ${#missing[@]} -gt 0 ]; then
        log_error "Missing prerequisites: ${missing[*]}"
        log_error "Please install them before running this script."
        exit 1
    fi

    # Verify Java version
    local java_version
    java_version=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
    if [ "$java_version" -lt 21 ] 2>/dev/null; then
        log_warn "Java 21+ recommended (found: $java_version)"
    fi

    # Verify Docker is running
    if ! docker info &>/dev/null; then
        log_error "Docker daemon is not running. Please start Docker first."
        exit 1
    fi

    log_success "All prerequisites met"
}

# Determine docker compose command
compose_cmd() {
    if docker compose version &>/dev/null 2>&1; then
        echo "docker compose"
    else
        echo "docker-compose"
    fi
}

run_compose() {
    $(compose_cmd) -f "${COMPOSE_FILE}" -f "${COMPOSE_LDAP_FILE}" "$@"
}

# =============================================================================
# Build Functions
# =============================================================================

build_maven() {
    local skip_tests="${1:-false}"
    log_step "Building Maven modules"

    local mvn_args="clean package -q"
    if [ "$skip_tests" = "true" ]; then
        mvn_args="$mvn_args -Dmaven.test.skip=true"
        log_info "Skipping tests (--skip-tests)"
    fi

    log_info "Running: mvn $mvn_args"
    if mvn $mvn_args -f "${SCRIPT_DIR}/pom.xml"; then
        log_success "Maven build completed"
    else
        log_error "Maven build failed!"
        exit 1
    fi
}

build_angular() {
    log_step "Building Angular frontend"

    local ui_dir="${SCRIPT_DIR}/squadron-ui"
    if [ ! -d "$ui_dir/node_modules" ]; then
        log_info "Installing npm dependencies..."
        (cd "$ui_dir" && npm ci --silent)
    fi

    log_info "Building production bundle..."
    if (cd "$ui_dir" && npx ng build --configuration=production 2>/dev/null); then
        log_success "Angular build completed"
    else
        log_error "Angular build failed!"
        exit 1
    fi
}

build_docker_images() {
    log_step "Building Docker images"

    # Build backend service images
    for service in "${BACKEND_SERVICES[@]}"; do
        local module="${service#squadron-}"  # Remove prefix
        local dockerfile="${SCRIPT_DIR}/squadron-${module}/Dockerfile"
        if [ -f "$dockerfile" ]; then
            log_info "Building ${service}..."
            docker build -t "squadron/${service}:latest" \
                -f "$dockerfile" \
                "${SCRIPT_DIR}" \
                --quiet 2>/dev/null || {
                log_error "Failed to build ${service}"
                exit 1
            }
            log_success "  ${service}:latest"
        else
            log_warn "  Dockerfile not found: $dockerfile (skipping)"
        fi
    done

    # Build UI image
    local ui_dockerfile="${SCRIPT_DIR}/squadron-ui/Dockerfile"
    if [ -f "$ui_dockerfile" ]; then
        log_info "Building squadron-ui..."
        docker build -t "squadron/squadron-ui:latest" \
            -f "$ui_dockerfile" \
            "${SCRIPT_DIR}/squadron-ui" \
            --quiet 2>/dev/null || {
            log_error "Failed to build squadron-ui"
            exit 1
        }
        log_success "  squadron-ui:latest"
    fi

    log_success "All Docker images built"
}

# =============================================================================
# Container Health Functions
# =============================================================================

# Get the health status of a container by service name.
# Returns: healthy, unhealthy, starting, none (no healthcheck), or empty (not running).
get_health_status() {
    local service="$1"
    local container_id
    container_id=$(run_compose ps -q "$service" 2>/dev/null) || true
    if [ -z "$container_id" ]; then
        echo ""
        return
    fi
    local status
    status=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id" 2>/dev/null) || true
    echo "$status"
}

# Wait for a single service to become healthy.
# Uses the Docker-native healthcheck defined in docker-compose.yml.
wait_for_healthy() {
    local service="$1"
    local max_wait="${2:-180}"
    local waited=0

    while true; do
        local status
        status=$(get_health_status "$service")

        case "$status" in
            healthy)
                return 0
                ;;
            unhealthy)
                log_error "$service is unhealthy. Showing recent logs:"
                run_compose logs --tail=15 "$service" 2>/dev/null || true
                return 1
                ;;
            "")
                # Container not running at all
                if [ $waited -ge 15 ]; then
                    log_error "$service container is not running."
                    run_compose logs --tail=10 "$service" 2>/dev/null || true
                    return 1
                fi
                ;;
            none|starting)
                # Still starting or no healthcheck, keep waiting
                ;;
        esac

        sleep 3
        waited=$((waited + 3))
        if [ $waited -ge $max_wait ]; then
            log_warn "$service did not become healthy within ${max_wait}s (status: ${status:-unknown})"
            return 1
        fi
    done
}

# =============================================================================
# Compose Functions
# =============================================================================

stop_previous() {
    log_step "Stopping any previously running containers"
    if run_compose --profile services --profile frontend down --remove-orphans 2>/dev/null; then
        log_success "Previous containers stopped"
    else
        log_info "No previous containers to stop"
    fi
}

start_infrastructure() {
    log_step "Starting infrastructure services"

    log_info "Pulling images (this may take a while on first run)..."
    run_compose pull --quiet "${INFRA_SERVICES[@]}" 2>/dev/null || true

    log_info "Starting PostgreSQL, Redis, NATS, Keycloak, Mailpit, PgBouncer, Ollama, OpenLDAP..."
    run_compose up -d --no-build "${INFRA_SERVICES[@]}" 2>/dev/null

    log_info "Waiting for infrastructure to become healthy..."

    local failed=()
    for service in "${HEALTHCHECK_SERVICES[@]}"; do
        local timeout=180
        # Keycloak and Ollama are slow to start
        if [ "$service" = "keycloak" ]; then
            timeout=240
        elif [ "$service" = "ollama" ]; then
            timeout=120
        fi

        log_info "  Waiting for ${service}..."
        if wait_for_healthy "$service" "$timeout"; then
            log_success "  ${service} is healthy"
        else
            failed+=("$service")
        fi
    done

    # PgBouncer and Mailpit don't have healthchecks; verify they're running
    for service in pgbouncer mailpit; do
        local container_id
        container_id=$(run_compose ps -q "$service" 2>/dev/null) || true
        if [ -n "$container_id" ]; then
            local state
            state=$(docker inspect --format='{{.State.Status}}' "$container_id" 2>/dev/null) || true
            if [ "$state" = "running" ]; then
                log_success "  ${service} is running"
            else
                log_warn "  ${service} is not running (state: ${state:-unknown})"
                failed+=("$service")
            fi
        else
            log_warn "  ${service} container was not created"
            failed+=("$service")
        fi
    done

    if [ ${#failed[@]} -gt 0 ]; then
        log_error "The following services failed to start: ${failed[*]}"
        log_error "Check logs with: $(basename "$0") --logs <service>"
        exit 1
    fi

    log_success "All infrastructure services are up and healthy"
}

start_services() {
    log_step "Starting Squadron services"

    log_info "Starting all backend services and frontend..."
    run_compose --profile services --profile frontend up -d --no-build 2>/dev/null

    log_info "Waiting for backend services to become healthy..."

    local failed=()
    for service in "${BACKEND_SERVICES[@]}"; do
        log_info "  Waiting for ${service}..."
        if wait_for_healthy "$service" 180; then
            log_success "  ${service} is healthy"
        else
            failed+=("$service")
        fi
    done

    # Wait for the UI (nginx starts fast once gateway is healthy)
    log_info "  Waiting for squadron-ui..."
    if wait_for_healthy "squadron-ui" 60; then
        log_success "  squadron-ui is healthy"
    else
        failed+=("squadron-ui")
    fi

    if [ ${#failed[@]} -gt 0 ]; then
        log_error "The following services failed to start: ${failed[*]}"
        log_error "Check logs with: $(basename "$0") --logs <service>"
        exit 1
    fi

    log_success "All Squadron services are up and healthy"
}

pull_ollama_model() {
    log_step "Pulling Ollama model"

    local model="${OLLAMA_MODEL:-qwen2.5-coder:7b}"
    log_info "Pulling model: $model"

    # Wait for Ollama to be ready
    local max_wait=60
    local waited=0
    while ! curl -sf http://localhost:11434/api/tags &>/dev/null; do
        sleep 2
        waited=$((waited + 2))
        if [ $waited -ge $max_wait ]; then
            log_warn "Ollama not responding. Skipping model pull."
            return 0
        fi
    done

    if curl -sf http://localhost:11434/api/tags | grep -q "$model" 2>/dev/null; then
        log_success "Model $model already available"
    else
        log_info "Downloading $model (this may take several minutes)..."
        if curl -sf http://localhost:11434/api/pull -d "{\"name\": \"$model\"}" >/dev/null; then
            log_success "Model $model pulled successfully"
        else
            log_warn "Failed to pull model. You can pull it later with:"
            log_warn "  curl http://localhost:11434/api/pull -d '{\"name\": \"$model\"}'"
        fi
    fi
}

stop_all() {
    log_step "Stopping all Squadron services (including test LDAP)"
    run_compose --profile services --profile frontend down --remove-orphans 2>/dev/null
    log_success "All services stopped"
}

clean_all() {
    log_step "Cleaning up all containers, volumes, and data"
    run_compose --profile services --profile frontend down -v --remove-orphans 2>/dev/null
    log_success "Clean complete -- all data removed"
}

show_status() {
    log_step "Squadron Service Status (with Test LDAP)"
    echo ""
    run_compose --profile services --profile frontend ps
}

show_logs() {
    local service="${1:-}"
    if [ -n "$service" ]; then
        run_compose --profile services --profile frontend logs -f "$service"
    else
        run_compose --profile services --profile frontend logs -f
    fi
}

print_access_info() {
    echo ""
    echo -e "${BOLD}${GREEN}=================================================${NC}"
    echo -e "${BOLD}${GREEN}  Squadron is running! (with Test LDAP)${NC}"
    echo -e "${BOLD}${GREEN}=================================================${NC}"
    echo ""
    echo -e "  ${BOLD}${GREEN}>>> Open in your browser: ${CYAN}http://localhost:4200${NC} ${BOLD}${GREEN}<<<${NC}"
    echo ""
    echo -e "${BOLD}Access Points:${NC}"
    echo -e "  ${CYAN}UI:${NC}           http://localhost:4200"
    echo -e "  ${CYAN}API Gateway:${NC}  http://localhost:8443"
    echo -e "  ${CYAN}Keycloak:${NC}     http://localhost:8080  (admin/admin)"
    echo -e "  ${CYAN}Mailpit:${NC}      http://localhost:8025"
    echo -e "  ${CYAN}NATS Monitor:${NC} http://localhost:8222"
    echo -e "  ${CYAN}Ollama:${NC}       http://localhost:11434"
    echo ""
    echo -e "${BOLD}Service Ports:${NC}"
    echo -e "  Identity:      8081    Config:        8082"
    echo -e "  Orchestrator:  8083    Platform:      8084"
    echo -e "  Agent:         8085    Workspace:     8086"
    echo -e "  Git:           8087    Review:        8088"
    echo -e "  Notification:  8089"
    echo ""
    echo -e "${BOLD}Infrastructure:${NC}"
    echo -e "  PostgreSQL:    5432    PgBouncer:     6432"
    echo -e "  Redis:         6379    NATS:          4222"
    echo ""
    echo -e "${BOLD}${MAGENTA}Test LDAP (Planet Express):${NC}"
    echo -e "  ${CYAN}LDAP:${NC}         ldap://localhost:10389"
    echo -e "  ${CYAN}LDAPS:${NC}        ldaps://localhost:10636"
    echo -e "  ${CYAN}Base DN:${NC}      dc=planetexpress,dc=com"
    echo -e "  ${CYAN}Admin DN:${NC}     cn=admin,dc=planetexpress,dc=com"
    echo -e "  ${CYAN}Admin Pass:${NC}   GoodNewsEveryone"
    echo -e "  ${CYAN}User Base:${NC}    ou=people,dc=planetexpress,dc=com"
    echo ""
    echo -e "  ${BOLD}Test Users (uid / password):${NC}"
    echo -e "    professor / professor    fry / fry          leela / leela"
    echo -e "    bender / bender          zoidberg / zoidberg"
    echo -e "    hermes / hermes          amy / amy"
    echo ""
    echo -e "  ${BOLD}Test with:${NC}"
    echo -e "    ldapsearch -H ldap://localhost:10389 -x -b \"ou=people,dc=planetexpress,dc=com\" \\"
    echo -e "      -D \"cn=admin,dc=planetexpress,dc=com\" -w GoodNewsEveryone \"(objectClass=inetOrgPerson)\""
    echo ""
    echo -e "${BOLD}Useful Commands:${NC}"
    echo -e "  $(basename "$0") --status      Show service status"
    echo -e "  $(basename "$0") --logs        Tail all logs"
    echo -e "  $(basename "$0") --stop        Stop all services"
    echo -e "  $(basename "$0") --clean       Remove everything (including data)"
    echo ""
}

# =============================================================================
# GPU Detection for Ollama
# =============================================================================

setup_ollama_gpu() {
    local no_gpu="${1:-false}"
    if [ "$no_gpu" = "true" ]; then
        log_info "GPU support disabled (--no-gpu)"
        return
    fi

    # Check for NVIDIA GPU
    if command -v nvidia-smi &>/dev/null && nvidia-smi &>/dev/null; then
        log_success "NVIDIA GPU detected -- Ollama will use GPU acceleration"
    else
        log_info "No NVIDIA GPU detected -- Ollama will run on CPU"
    fi
}

# =============================================================================
# Main
# =============================================================================

main() {
    local skip_build=false
    local skip_tests=false
    local infra_only=false
    local do_clean=false
    local do_stop=false
    local do_status=false
    local do_logs=false
    local log_service=""
    local do_pull_model=false
    local no_gpu=false

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --help|-h)
                usage
                ;;
            --skip-build)
                skip_build=true
                shift
                ;;
            --skip-tests)
                skip_tests=true
                shift
                ;;
            --infra)
                infra_only=true
                shift
                ;;
            --clean)
                do_clean=true
                shift
                ;;
            --stop)
                do_stop=true
                shift
                ;;
            --status)
                do_status=true
                shift
                ;;
            --logs)
                do_logs=true
                if [[ $# -gt 1 && ! "$2" =~ ^-- ]]; then
                    log_service="$2"
                    shift
                fi
                shift
                ;;
            --pull-model)
                do_pull_model=true
                shift
                ;;
            --no-gpu)
                no_gpu=true
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                echo "Run '$(basename "$0") --help' for usage."
                exit 1
                ;;
        esac
    done

    # Handle simple commands first
    if [ "$do_stop" = true ]; then
        stop_all
        exit 0
    fi

    if [ "$do_status" = true ]; then
        show_status
        exit 0
    fi

    if [ "$do_logs" = true ]; then
        show_logs "$log_service"
        exit 0
    fi

    # Banner
    echo ""
    echo -e "${BOLD}${CYAN}"
    echo "  ███████╗ ██████╗ ██╗   ██╗ █████╗ ██████╗ ██████╗  ██████╗ ███╗   ██╗"
    echo "  ██╔════╝██╔═══██╗██║   ██║██╔══██╗██╔══██╗██╔══██╗██╔═══██╗████╗  ██║"
    echo "  ███████╗██║   ██║██║   ██║███████║██║  ██║██████╔╝██║   ██║██╔██╗ ██║"
    echo "  ╚════██║██║▄▄ ██║██║   ██║██╔══██║██║  ██║██╔══██╗██║   ██║██║╚██╗██║"
    echo "  ███████║╚██████╔╝╚██████╔╝██║  ██║██████╔╝██║  ██║╚██████╔╝██║ ╚████║"
    echo "  ╚══════╝ ╚══▀▀═╝  ╚═════╝ ╚═╝  ╚═╝╚═════╝ ╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═══╝"
    echo -e "${NC}"
    echo -e "  ${BOLD}AI-Powered Software Development Workflow Platform${NC}"
    echo -e "  ${MAGENTA}${BOLD}+ Test LDAP (Planet Express)${NC}"
    echo ""

    # Clean if requested
    if [ "$do_clean" = true ]; then
        clean_all
    fi

    # Prerequisites check
    check_prerequisites

    # Stop any previously running containers from this project
    stop_previous

    # Build phase
    if [ "$skip_build" = false ]; then
        build_maven "$skip_tests"
        build_angular
        build_docker_images
    else
        log_info "Skipping build (--skip-build)"
    fi

    # GPU setup
    setup_ollama_gpu "$no_gpu"

    # Start infrastructure (including test LDAP)
    start_infrastructure

    # Start application services (unless infra-only)
    if [ "$infra_only" = false ]; then
        start_services
    else
        log_info "Infrastructure-only mode (--infra). Services not started."
        log_info "Start services with: $(basename "$0") --skip-build"
    fi

    # Pull Ollama model if requested
    if [ "$do_pull_model" = true ]; then
        pull_ollama_model
    fi

    # Print access info
    if [ "$infra_only" = false ]; then
        print_access_info
    else
        echo ""
        echo -e "${BOLD}${GREEN}Infrastructure is running! (with Test LDAP)${NC}"
        echo ""
        echo -e "  ${CYAN}PostgreSQL:${NC}  localhost:5432"
        echo -e "  ${CYAN}PgBouncer:${NC}   localhost:6432"
        echo -e "  ${CYAN}Redis:${NC}       localhost:6379"
        echo -e "  ${CYAN}NATS:${NC}        localhost:4222 (monitor: 8222)"
        echo -e "  ${CYAN}Keycloak:${NC}    localhost:8080 (admin/admin)"
        echo -e "  ${CYAN}Mailpit:${NC}     localhost:8025"
        echo -e "  ${CYAN}Ollama:${NC}      localhost:11434"
        echo ""
        echo -e "  ${MAGENTA}${BOLD}Test LDAP:${NC}"
        echo -e "  ${CYAN}LDAP:${NC}        ldap://localhost:10389"
        echo -e "  ${CYAN}LDAPS:${NC}       ldaps://localhost:10636"
        echo -e "  ${CYAN}Admin DN:${NC}    cn=admin,dc=planetexpress,dc=com"
        echo -e "  ${CYAN}Admin Pass:${NC}  GoodNewsEveryone"
        echo -e "  ${CYAN}User Base:${NC}   ou=people,dc=planetexpress,dc=com"
        echo ""
        echo -e "  ${BOLD}Test Users:${NC}  professor, fry, leela, bender, zoidberg, hermes, amy"
        echo -e "               (password = uid, e.g. fry/fry)"
        echo ""
        echo -e "Run services with: ${BOLD}mvn spring-boot:run${NC} from each module directory"
        echo ""
    fi
}

main "$@"
