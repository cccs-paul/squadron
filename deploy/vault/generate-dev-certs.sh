#!/usr/bin/env bash
# =============================================================================
# Squadron - Generate Self-Signed Development TLS Certificates
# =============================================================================
# Creates a self-signed CA and per-service certificates for local development
# with Docker Compose. This is NOT for production use -- production deployments
# should use Vault PKI + cert-manager.
#
# Usage:
#   ./generate-dev-certs.sh [--output-dir ./certs] [--domain squadron.local]
#
# Output structure:
#   <output-dir>/
#     ca.crt                    # CA certificate
#     ca.key                    # CA private key
#     squadron-gateway/
#       tls.crt                 # Server certificate (includes CA chain)
#       tls.key                 # Server private key
#     squadron-identity/
#       ...
# =============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
OUTPUT_DIR="${OUTPUT_DIR:-./certs}"
DOMAIN="${DOMAIN:-squadron.local}"
CA_DAYS=3650                    # CA valid for 10 years
CERT_DAYS=365                   # Service certs valid for 1 year
KEY_TYPE="ec"                   # Use ECDSA keys
EC_CURVE="prime256v1"           # P-256 curve

# All Squadron services
SERVICES=(
    "squadron-gateway:8443"
    "squadron-identity:8081"
    "squadron-config:8082"
    "squadron-orchestrator:8083"
    "squadron-platform:8084"
    "squadron-agent:8085"
    "squadron-workspace:8086"
    "squadron-git:8087"
    "squadron-review:8088"
    "squadron-notification:8089"
    "squadron-ui:80"
    "squadron-postgresql:5432"
    "squadron-redis:6379"
    "squadron-nats:4222"
    "squadron-keycloak:8080"
)

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --domain)
            DOMAIN="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [--output-dir <dir>] [--domain <domain>]"
            echo ""
            echo "Options:"
            echo "  --output-dir  Output directory for certificates (default: ./certs)"
            echo "  --domain      Base domain (default: squadron.local)"
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
    echo "[$(date '+%H:%M:%S')] $*"
}

# ---------------------------------------------------------------------------
# Prerequisites check
# ---------------------------------------------------------------------------
if ! command -v openssl &>/dev/null; then
    echo "ERROR: openssl is required but not found"
    exit 1
fi

log "Generating development TLS certificates"
log "Output directory: $OUTPUT_DIR"
log "Domain: $DOMAIN"

mkdir -p "$OUTPUT_DIR"

# ---------------------------------------------------------------------------
# Step 1: Generate CA
# ---------------------------------------------------------------------------
log "Generating CA key and certificate..."

openssl ecparam -genkey -name "$EC_CURVE" -out "$OUTPUT_DIR/ca.key" 2>/dev/null

openssl req -new -x509 \
    -key "$OUTPUT_DIR/ca.key" \
    -out "$OUTPUT_DIR/ca.crt" \
    -days "$CA_DAYS" \
    -subj "/CN=Squadron Dev CA/O=Squadron/OU=Development" \
    -addext "basicConstraints=critical,CA:TRUE,pathlen:1" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" \
    2>/dev/null

log "CA certificate generated: $OUTPUT_DIR/ca.crt"

# ---------------------------------------------------------------------------
# Step 2: Generate per-service certificates
# ---------------------------------------------------------------------------
for entry in "${SERVICES[@]}"; do
    IFS=':' read -r svc_name svc_port <<< "$entry"
    svc_dir="$OUTPUT_DIR/$svc_name"
    mkdir -p "$svc_dir"

    log "Generating certificate for $svc_name..."

    # Generate private key
    openssl ecparam -genkey -name "$EC_CURVE" -out "$svc_dir/tls.key" 2>/dev/null

    # Build SAN extension
    san="DNS:${svc_name},DNS:${svc_name}.squadron,DNS:${svc_name}.squadron.svc,DNS:${svc_name}.squadron.svc.cluster.local,DNS:localhost,IP:127.0.0.1"

    # For the UI service, also add the domain wildcard
    if [ "$svc_name" = "squadron-ui" ] || [ "$svc_name" = "squadron-gateway" ]; then
        san="${san},DNS:${DOMAIN},DNS:*.${DOMAIN}"
    fi

    # Create CSR
    openssl req -new \
        -key "$svc_dir/tls.key" \
        -out "$svc_dir/tls.csr" \
        -subj "/CN=${svc_name}/O=Squadron/OU=Services" \
        2>/dev/null

    # Create extensions file
    cat > "$svc_dir/ext.cnf" <<EOF
[v3_ext]
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth,clientAuth
subjectAltName=${san}
EOF

    # Sign with CA
    openssl x509 -req \
        -in "$svc_dir/tls.csr" \
        -CA "$OUTPUT_DIR/ca.crt" \
        -CAkey "$OUTPUT_DIR/ca.key" \
        -CAcreateserial \
        -out "$svc_dir/server.crt" \
        -days "$CERT_DAYS" \
        -extfile "$svc_dir/ext.cnf" \
        -extensions v3_ext \
        2>/dev/null

    # Create full chain (server cert + CA cert)
    cat "$svc_dir/server.crt" "$OUTPUT_DIR/ca.crt" > "$svc_dir/tls.crt"

    # Clean up temporary files
    rm -f "$svc_dir/tls.csr" "$svc_dir/ext.cnf" "$svc_dir/server.crt"
done

# Remove CA serial file
rm -f "$OUTPUT_DIR/ca.srl"

# ---------------------------------------------------------------------------
# Step 3: Generate Java trust store (for services that need JKS)
# ---------------------------------------------------------------------------
log "Generating Java trust store..."

if command -v keytool &>/dev/null; then
    keytool -importcert \
        -alias squadron-ca \
        -file "$OUTPUT_DIR/ca.crt" \
        -keystore "$OUTPUT_DIR/truststore.jks" \
        -storepass changeit \
        -noprompt \
        2>/dev/null
    log "Java trust store: $OUTPUT_DIR/truststore.jks (password: changeit)"
else
    log "WARNING: keytool not found, skipping JKS trust store generation"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
log "====================================="
log "Development certificates generated!"
log "====================================="
echo ""
echo "Files created:"
echo "  CA certificate:  $OUTPUT_DIR/ca.crt"
echo "  CA private key:  $OUTPUT_DIR/ca.key"
for entry in "${SERVICES[@]}"; do
    IFS=':' read -r svc_name _ <<< "$entry"
    echo "  ${svc_name}:  $OUTPUT_DIR/$svc_name/tls.{crt,key}"
done
echo ""
echo "To use with Docker Compose, add volume mounts in docker-compose.override.yml:"
echo ""
echo "  services:"
echo "    squadron-gateway:"
echo "      volumes:"
echo "        - ./certs/squadron-gateway:/etc/tls:ro"
echo "        - ./certs/ca.crt:/etc/tls/ca.crt:ro"
echo "      environment:"
echo "        SERVER_SSL_ENABLED: 'true'"
echo "        SERVER_SSL_KEY_STORE_TYPE: PEM"
echo "        SERVER_SSL_KEY_STORE: /etc/tls/tls.crt"
echo "        SERVER_SSL_KEY_STORE_KEY: /etc/tls/tls.key"
echo "        SERVER_SSL_TRUST_STORE_TYPE: PEM"
echo "        SERVER_SSL_TRUST_STORE: /etc/tls/ca.crt"
echo "        SERVER_SSL_CLIENT_AUTH: need"
