# =============================================================================
# Squadron - Vault ACL Policy for Application Services
# =============================================================================
# This policy grants Squadron services read access to their secrets and
# the CA trust chain.
#
# Usage:
#   vault policy write squadron-service deploy/vault/squadron-service-policy.hcl
# =============================================================================

# -----------------------------------------------------------------------
# PKI CA Chain - Trust Bundle (for building TLS trust stores)
# -----------------------------------------------------------------------

path "pki_int/ca/pem" {
  capabilities = ["read"]
}

path "pki_int/ca_chain" {
  capabilities = ["read"]
}

path "pki/ca/pem" {
  capabilities = ["read"]
}

# -----------------------------------------------------------------------
# Database Credentials (dynamic secrets)
# -----------------------------------------------------------------------

# Each service gets its own database credential path
path "database/creds/squadron-identity" {
  capabilities = ["read"]
}

path "database/creds/squadron-orchestrator" {
  capabilities = ["read"]
}

path "database/creds/squadron-platform" {
  capabilities = ["read"]
}

path "database/creds/squadron-agent" {
  capabilities = ["read"]
}

path "database/creds/squadron-git" {
  capabilities = ["read"]
}

path "database/creds/squadron-review" {
  capabilities = ["read"]
}

path "database/creds/squadron-config" {
  capabilities = ["read"]
}

path "database/creds/squadron-notification" {
  capabilities = ["read"]
}

path "database/creds/squadron-workspace" {
  capabilities = ["read"]
}

# -----------------------------------------------------------------------
# KV Secrets - Application Configuration
# -----------------------------------------------------------------------

# Allow services to read application secrets (API keys, tokens, etc.)
path "secret/data/squadron/*" {
  capabilities = ["read"]
}

path "secret/metadata/squadron/*" {
  capabilities = ["read", "list"]
}

# Service-specific secrets
path "secret/data/squadron/{{identity.entity.aliases.*.name}}/*" {
  capabilities = ["read"]
}

# -----------------------------------------------------------------------
# Transit Engine - Encryption Operations (for encrypting sensitive data)
# -----------------------------------------------------------------------

path "transit/encrypt/squadron" {
  capabilities = ["update"]
}

path "transit/decrypt/squadron" {
  capabilities = ["update"]
}

# -----------------------------------------------------------------------
# Token Self-Management
# -----------------------------------------------------------------------

path "auth/token/renew-self" {
  capabilities = ["update"]
}

path "auth/token/lookup-self" {
  capabilities = ["read"]
}
