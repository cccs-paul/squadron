#!/usr/bin/env bash
# =============================================================================
# Squadron - Vault PKI Secrets Engine Setup
# =============================================================================
# Configures HashiCorp Vault as the PKI root/intermediate CA for Squadron's
# mTLS infrastructure. Run this once during initial cluster setup.
#
# Prerequisites:
#   - Vault server running and unsealed
#   - VAULT_ADDR and VAULT_TOKEN environment variables set
#   - vault CLI installed
#
# Usage:
#   export VAULT_ADDR=https://vault.squadron.local:8200
#   export VAULT_TOKEN=<root-or-admin-token>
#   ./setup-pki.sh [--domain squadron.local] [--ttl 87600h] [--dry-run]
# =============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration defaults
# ---------------------------------------------------------------------------
DOMAIN="${DOMAIN:-squadron.local}"
ROOT_TTL="${ROOT_TTL:-87600h}"          # 10 years for root CA
INTERMEDIATE_TTL="${INTERMEDIATE_TTL:-43800h}"  # 5 years for intermediate CA
CERT_TTL="${CERT_TTL:-8760h}"           # 1 year default for issued certs
MAX_CERT_TTL="${MAX_CERT_TTL:-17520h}"  # 2 years max for issued certs
DRY_RUN=false

# Squadron service names (must match Kubernetes service DNS names)
SERVICES=(
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

# Infrastructure services
INFRA_SERVICES=(
    "squadron-postgresql"
    "squadron-redis"
    "squadron-nats"
    "squadron-keycloak"
)

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --domain)
            DOMAIN="$2"
            shift 2
            ;;
        --ttl)
            ROOT_TTL="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--domain <domain>] [--ttl <root-ca-ttl>] [--dry-run]"
            echo ""
            echo "Options:"
            echo "  --domain    Base domain for certificates (default: squadron.local)"
            echo "  --ttl       Root CA TTL (default: 87600h / 10 years)"
            echo "  --dry-run   Print commands without executing"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

run() {
    if [ "$DRY_RUN" = true ]; then
        echo "[DRY RUN] $*"
    else
        "$@"
    fi
}

check_prereqs() {
    if ! command -v vault &>/dev/null; then
        echo "ERROR: vault CLI not found. Install from https://releases.hashicorp.com/vault/"
        exit 1
    fi

    if [ -z "${VAULT_ADDR:-}" ]; then
        echo "ERROR: VAULT_ADDR environment variable not set"
        exit 1
    fi

    if [ -z "${VAULT_TOKEN:-}" ]; then
        echo "ERROR: VAULT_TOKEN environment variable not set"
        exit 1
    fi

    # Verify connectivity
    if ! vault status &>/dev/null; then
        echo "ERROR: Cannot connect to Vault at $VAULT_ADDR"
        exit 1
    fi

    log "Connected to Vault at $VAULT_ADDR"
}

# ---------------------------------------------------------------------------
# Step 1: Enable and configure Root CA PKI
# ---------------------------------------------------------------------------
setup_root_ca() {
    log "=== Step 1: Setting up Root CA PKI engine ==="

    # Enable the root PKI secrets engine
    if vault secrets list -format=json | jq -e '."pki/"' &>/dev/null; then
        log "Root PKI engine already enabled at pki/"
    else
        run vault secrets enable pki
        log "Enabled root PKI engine at pki/"
    fi

    # Tune the max TTL
    run vault secrets tune -max-lease-ttl="$ROOT_TTL" pki

    # Generate the root CA certificate
    log "Generating root CA certificate..."
    run vault write -format=json pki/root/generate/internal \
        common_name="Squadron Root CA" \
        organization="Squadron" \
        ou="Infrastructure" \
        ttl="$ROOT_TTL" \
        key_type="ec" \
        key_bits=384 \
        | jq -r '.data.certificate' > /tmp/squadron-root-ca.pem

    log "Root CA certificate saved to /tmp/squadron-root-ca.pem"

    # Configure the CA and CRL distribution points
    run vault write pki/config/urls \
        issuing_certificates="${VAULT_ADDR}/v1/pki/ca" \
        crl_distribution_points="${VAULT_ADDR}/v1/pki/crl"

    log "Root CA setup complete"
}

# ---------------------------------------------------------------------------
# Step 2: Enable and configure Intermediate CA PKI
# ---------------------------------------------------------------------------
setup_intermediate_ca() {
    log "=== Step 2: Setting up Intermediate CA PKI engine ==="

    # Enable the intermediate PKI secrets engine
    if vault secrets list -format=json | jq -e '."pki_int/"' &>/dev/null; then
        log "Intermediate PKI engine already enabled at pki_int/"
    else
        run vault secrets enable -path=pki_int pki
        log "Enabled intermediate PKI engine at pki_int/"
    fi

    # Tune the max TTL
    run vault secrets tune -max-lease-ttl="$INTERMEDIATE_TTL" pki_int

    # Generate intermediate CSR
    log "Generating intermediate CA CSR..."
    run vault write -format=json pki_int/intermediate/generate/internal \
        common_name="Squadron Intermediate CA" \
        organization="Squadron" \
        ou="Services" \
        key_type="ec" \
        key_bits=256 \
        | jq -r '.data.csr' > /tmp/squadron-intermediate.csr

    # Sign the intermediate CSR with the root CA
    log "Signing intermediate CA with root CA..."
    run vault write -format=json pki/root/sign-intermediate \
        csr=@/tmp/squadron-intermediate.csr \
        format=pem_bundle \
        ttl="$INTERMEDIATE_TTL" \
        | jq -r '.data.certificate' > /tmp/squadron-intermediate.pem

    # Import the signed intermediate certificate
    run vault write pki_int/intermediate/set-signed \
        certificate=@/tmp/squadron-intermediate.pem

    # Configure the CA and CRL distribution points
    run vault write pki_int/config/urls \
        issuing_certificates="${VAULT_ADDR}/v1/pki_int/ca" \
        crl_distribution_points="${VAULT_ADDR}/v1/pki_int/crl"

    log "Intermediate CA setup complete"

    # Clean up temporary files
    rm -f /tmp/squadron-intermediate.csr /tmp/squadron-intermediate.pem
}

# ---------------------------------------------------------------------------
# Step 3: Create PKI roles for Squadron services
# ---------------------------------------------------------------------------
create_service_roles() {
    log "=== Step 3: Creating PKI roles for Squadron services ==="

    # Build the list of allowed domains for all services
    local allowed_domains="${DOMAIN}"
    for svc in "${SERVICES[@]}" "${INFRA_SERVICES[@]}"; do
        allowed_domains="${allowed_domains},${svc},${svc}.squadron,${svc}.squadron.svc,${svc}.squadron.svc.cluster.local"
    done

    # Create a role for Squadron application services
    log "Creating 'squadron-service' role..."
    run vault write pki_int/roles/squadron-service \
        allowed_domains="$allowed_domains" \
        allow_subdomains=true \
        allow_bare_domains=true \
        allow_localhost=true \
        enforce_hostnames=true \
        server_flag=true \
        client_flag=true \
        key_type="ec" \
        key_bits=256 \
        max_ttl="$MAX_CERT_TTL" \
        ttl="$CERT_TTL" \
        require_cn=false \
        generate_lease=true \
        no_store=false \
        organization="Squadron" \
        ou="Services"

    # Create a role for infrastructure services (longer TTL allowed)
    log "Creating 'squadron-infra' role..."
    run vault write pki_int/roles/squadron-infra \
        allowed_domains="$allowed_domains" \
        allow_subdomains=true \
        allow_bare_domains=true \
        allow_localhost=true \
        enforce_hostnames=true \
        server_flag=true \
        client_flag=true \
        key_type="ec" \
        key_bits=256 \
        max_ttl="$MAX_CERT_TTL" \
        ttl="$CERT_TTL" \
        require_cn=false \
        generate_lease=true \
        no_store=false \
        organization="Squadron" \
        ou="Infrastructure"

    log "PKI roles created"
}

# ---------------------------------------------------------------------------
# Step 4: Create Vault policies
# ---------------------------------------------------------------------------
create_policies() {
    log "=== Step 4: Creating Vault policies ==="

    # cert-manager policy (for Kubernetes cert-manager to issue certs)
    run vault policy write squadron-cert-manager - <<'POLICY'
# Allow cert-manager to issue certificates via the intermediate CA
path "pki_int/sign/squadron-service" {
  capabilities = ["create", "update"]
}

path "pki_int/sign/squadron-infra" {
  capabilities = ["create", "update"]
}

path "pki_int/issue/squadron-service" {
  capabilities = ["create"]
}

path "pki_int/issue/squadron-infra" {
  capabilities = ["create"]
}

# Allow reading CA chain for trust bundle distribution
path "pki_int/ca/pem" {
  capabilities = ["read"]
}

path "pki_int/ca_chain" {
  capabilities = ["read"]
}

path "pki/ca/pem" {
  capabilities = ["read"]
}
POLICY
    log "Created 'squadron-cert-manager' policy"

    # Service policy (for services to read their own certs from K8s secrets)
    run vault policy write squadron-service - <<'POLICY'
# Allow services to read PKI CA chain for trust store
path "pki_int/ca/pem" {
  capabilities = ["read"]
}

path "pki_int/ca_chain" {
  capabilities = ["read"]
}

path "pki/ca/pem" {
  capabilities = ["read"]
}

# Allow services to read their database credentials
path "database/creds/squadron-*" {
  capabilities = ["read"]
}

# Allow services to read KV secrets
path "secret/data/squadron/*" {
  capabilities = ["read"]
}

path "secret/metadata/squadron/*" {
  capabilities = ["read", "list"]
}
POLICY
    log "Created 'squadron-service' policy"
}

# ---------------------------------------------------------------------------
# Step 5: Configure Kubernetes auth method
# ---------------------------------------------------------------------------
setup_k8s_auth() {
    log "=== Step 5: Configuring Kubernetes auth method ==="

    # Enable Kubernetes auth if not already enabled
    if vault auth list -format=json | jq -e '."kubernetes/"' &>/dev/null; then
        log "Kubernetes auth method already enabled"
    else
        run vault auth enable kubernetes
        log "Enabled Kubernetes auth method"
    fi

    # Configure Kubernetes auth (assumes Vault is running in K8s or has access)
    # These values should be overridden for your specific cluster
    local k8s_host="${KUBERNETES_SERVICE_HOST:-https://kubernetes.default.svc}"
    local k8s_port="${KUBERNETES_SERVICE_PORT:-443}"

    log "Configuring Kubernetes auth for host: ${k8s_host}:${k8s_port}"
    log "NOTE: If running outside K8s, set KUBERNETES_SERVICE_HOST and provide CA cert"

    # If running inside K8s, the service account token and CA are auto-mounted
    if [ -f /var/run/secrets/kubernetes.io/serviceaccount/token ]; then
        run vault write auth/kubernetes/config \
            kubernetes_host="https://${k8s_host}:${k8s_port}" \
            token_reviewer_jwt="$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
            kubernetes_ca_cert="$(cat /var/run/secrets/kubernetes.io/serviceaccount/ca.crt)"
    else
        log "WARNING: Not running in K8s. Skipping auto-config."
        log "Configure manually with:"
        log "  vault write auth/kubernetes/config \\"
        log "    kubernetes_host=\"https://<k8s-api>:6443\" \\"
        log "    token_reviewer_jwt=\"<service-account-token>\" \\"
        log "    kubernetes_ca_cert=@<path-to-k8s-ca.crt>"
    fi

    # Create Kubernetes auth roles for cert-manager
    run vault write auth/kubernetes/role/cert-manager \
        bound_service_account_names="cert-manager" \
        bound_service_account_namespaces="cert-manager" \
        policies="squadron-cert-manager" \
        ttl=1h

    # Create Kubernetes auth roles for Squadron services
    for svc in "${SERVICES[@]}"; do
        local sa_name="${svc}"
        run vault write "auth/kubernetes/role/${svc}" \
            bound_service_account_names="${sa_name}" \
            bound_service_account_namespaces="squadron" \
            policies="squadron-service" \
            ttl=1h
    done

    log "Kubernetes auth roles created"
}

# ---------------------------------------------------------------------------
# Step 6: Issue a test certificate
# ---------------------------------------------------------------------------
issue_test_cert() {
    log "=== Step 6: Issuing test certificate ==="

    local test_output
    test_output=$(vault write -format=json pki_int/issue/squadron-service \
        common_name="squadron-gateway.squadron.svc.cluster.local" \
        alt_names="squadron-gateway,squadron-gateway.squadron,squadron-gateway.squadron.svc" \
        ip_sans="127.0.0.1" \
        ttl="720h" 2>&1) || {
        echo "ERROR: Failed to issue test certificate"
        echo "$test_output"
        return 1
    }

    local serial
    serial=$(echo "$test_output" | jq -r '.data.serial_number')
    local expiration
    expiration=$(echo "$test_output" | jq -r '.data.expiration')

    log "Test certificate issued successfully"
    log "  Serial: $serial"
    log "  Expiration: $(date -d @"$expiration" 2>/dev/null || echo "$expiration")"

    # Revoke the test certificate
    run vault write pki_int/revoke serial_number="$serial"
    log "Test certificate revoked"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    log "=========================================="
    log "Squadron Vault PKI Setup"
    log "=========================================="
    log "Domain: $DOMAIN"
    log "Root CA TTL: $ROOT_TTL"
    log "Intermediate CA TTL: $INTERMEDIATE_TTL"
    log "Default cert TTL: $CERT_TTL"
    log "Max cert TTL: $MAX_CERT_TTL"
    log "Dry run: $DRY_RUN"
    log "=========================================="

    check_prereqs
    setup_root_ca
    setup_intermediate_ca
    create_service_roles
    create_policies
    setup_k8s_auth
    issue_test_cert

    log "=========================================="
    log "Vault PKI setup complete!"
    log "=========================================="
    log ""
    log "Next steps:"
    log "  1. Install cert-manager in your K8s cluster:"
    log "     kubectl apply -f https://github.com/cert-manager/cert-manager/releases/latest/download/cert-manager.yaml"
    log ""
    log "  2. Apply the cert-manager ClusterIssuer:"
    log "     kubectl apply -f deploy/vault/cert-manager/cluster-issuer.yaml"
    log ""
    log "  3. Apply service Certificate resources:"
    log "     kubectl apply -f deploy/vault/cert-manager/certificates.yaml"
    log ""
    log "  4. Deploy Squadron with mTLS values:"
    log "     helm upgrade --install squadron deploy/helm/squadron -f deploy/vault/values-mtls.yaml"
    log ""
    log "  Root CA certificate: /tmp/squadron-root-ca.pem"
}

main
