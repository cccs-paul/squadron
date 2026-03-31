#!/usr/bin/env bash
# =============================================================================
# Squadron - Stop All Containers
#
# Stops all Squadron containers launched by testldap-build-and-start.sh,
# including infrastructure, backend services, frontend, and test LDAP.
#
# Usage:
#   ./testldap-stop.sh              # Stop all containers (preserve data)
#   ./testldap-stop.sh --clean      # Stop all containers and remove volumes/data
#   ./testldap-stop.sh --status     # Show current container status, then exit
#   ./testldap-stop.sh --help       # Show this help message
# =============================================================================
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="${SCRIPT_DIR}/deploy/docker"
COMPOSE_FILE="${COMPOSE_DIR}/docker-compose.yml"
COMPOSE_LDAP_FILE="${COMPOSE_DIR}/docker-compose-testldap.yml"

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
${BOLD}Squadron - Stop All Containers${NC}

Usage: $(basename "$0") [OPTIONS]

Options:
  --clean      Stop all containers AND remove volumes/data (fresh start next time)
  --status     Show current status of all Squadron containers, then exit
  --help       Show this help message

Examples:
  $(basename "$0")            # Stop all containers (data preserved in volumes)
  $(basename "$0") --clean    # Stop and wipe all data (PostgreSQL, Redis, NATS, etc.)
  $(basename "$0") --status   # Just check what's running

EOF
    exit 0
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
    $(compose_cmd) -f "${COMPOSE_FILE}" -f "${COMPOSE_LDAP_FILE}" --profile services --profile frontend "$@"
}

show_status() {
    log_step "Squadron Container Status"
    echo ""
    local running
    running=$(run_compose ps -q 2>/dev/null | wc -l)
    if [ "$running" -gt 0 ]; then
        run_compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
        echo ""
        log_info "${running} container(s) running"
    else
        log_info "No Squadron containers are currently running."
    fi
}

stop_all() {
    log_step "Stopping all Squadron containers"

    local running
    running=$(run_compose ps -q 2>/dev/null | wc -l)
    if [ "$running" -eq 0 ]; then
        log_info "No Squadron containers are currently running."
        return 0
    fi

    log_info "Found ${running} running container(s) -- stopping..."
    run_compose down --remove-orphans 2>&1 | while IFS= read -r line; do
        [[ -n "$line" ]] && log_info "  $line"
    done
    log_success "All Squadron containers stopped"
}

clean_all() {
    log_step "Stopping all Squadron containers and removing volumes"

    local running
    running=$(run_compose ps -q 2>/dev/null | wc -l)
    if [ "$running" -eq 0 ]; then
        log_info "No Squadron containers are currently running."
        log_info "Removing volumes and orphans anyway..."
    else
        log_info "Found ${running} running container(s) -- stopping and cleaning..."
    fi

    run_compose down -v --remove-orphans 2>&1 | while IFS= read -r line; do
        [[ -n "$line" ]] && log_info "  $line"
    done
    log_success "All containers stopped and data volumes removed"
    log_warn "All data (PostgreSQL, Redis, NATS streams, etc.) has been deleted."
    log_warn "Next startup will initialize fresh databases."
}

# =============================================================================
# Main
# =============================================================================

main() {
    local do_clean=false
    local do_status=false

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --help|-h)
                usage
                ;;
            --clean)
                do_clean=true
                shift
                ;;
            --status)
                do_status=true
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                echo "Run '$(basename "$0") --help' for usage."
                exit 1
                ;;
        esac
    done

    # Verify Docker is running
    if ! docker info &>/dev/null; then
        log_error "Docker daemon is not running."
        exit 1
    fi

    if [ "$do_status" = true ]; then
        show_status
        exit 0
    fi

    if [ "$do_clean" = true ]; then
        clean_all
    else
        stop_all
    fi
}

main "$@"
