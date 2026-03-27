# =============================================================================
# Squadron - Vault ACL Policy for cert-manager
# =============================================================================
# This policy grants cert-manager the ability to issue certificates using
# the Squadron intermediate CA.
#
# Usage:
#   vault policy write squadron-cert-manager deploy/vault/squadron-cert-manager-policy.hcl
# =============================================================================

# -----------------------------------------------------------------------
# PKI Intermediate CA - Certificate Issuance
# -----------------------------------------------------------------------

# Allow cert-manager to issue certificates for Squadron application services
path "pki_int/sign/squadron-service" {
  capabilities = ["create", "update"]
}

# Allow cert-manager to issue certificates for Squadron infrastructure services
path "pki_int/sign/squadron-infra" {
  capabilities = ["create", "update"]
}

# Allow cert-manager to issue certificates (full cert + key response)
path "pki_int/issue/squadron-service" {
  capabilities = ["create"]
}

path "pki_int/issue/squadron-infra" {
  capabilities = ["create"]
}

# -----------------------------------------------------------------------
# PKI CA Chain - Trust Bundle
# -----------------------------------------------------------------------

# Allow reading the intermediate CA certificate (for trust stores)
path "pki_int/ca/pem" {
  capabilities = ["read"]
}

# Allow reading the full CA chain
path "pki_int/ca_chain" {
  capabilities = ["read"]
}

# Allow reading the root CA certificate
path "pki/ca/pem" {
  capabilities = ["read"]
}

# -----------------------------------------------------------------------
# PKI CRL - Certificate Revocation Lists
# -----------------------------------------------------------------------

# Allow reading CRL for certificate validation
path "pki_int/crl" {
  capabilities = ["read"]
}

path "pki/crl" {
  capabilities = ["read"]
}
