# Air-Gap Deployment Runbook

This document covers deploying Squadron in air-gapped (disconnected) environments
where there is no access to external networks, container registries, or package
repositories.

## Overview

An air-gap deployment packages all required container images, Helm charts, and
configuration into a portable bundle that can be transferred to isolated
environments via physical media (USB, external drive) or a one-way data diode.

### Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                   Air-Gapped Environment                     │
│                                                              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐   │
│  │  Internal     │    │  Kubernetes  │    │   Ollama     │   │
│  │  Registry     │───>│  Cluster     │───>│   (Local AI) │   │
│  │  :5000        │    │              │    │              │   │
│  └──────────────┘    └──────────────┘    └──────────────┘   │
│         ▲                                                    │
│         │ docker load + push                                 │
│  ┌──────────────┐                                           │
│  │  squadron-    │                                           │
│  │  images.tar.gz│  <── Transferred via USB / data diode    │
│  └──────────────┘                                           │
└──────────────────────────────────────────────────────────────┘
```

---

## Prerequisites

### Air-Gapped Environment Requirements

| Component | Requirement |
|-----------|-------------|
| **Kubernetes** | v1.28+ cluster (or Docker/Podman for non-K8s deployments) |
| **Container Registry** | Docker Registry v2 (e.g., Harbor, Nexus, or plain `registry:2`) |
| **PostgreSQL** | 17.x |
| **Redis** | 7.x |
| **NATS** | 2.x with JetStream enabled |
| **Keycloak** | 26.x |
| **Ollama** | Latest (with GPU access for AI inference) |
| **Helm** | 3.14+ |
| **DNS** | Internal DNS server or `/etc/hosts` entries |
| **TLS** | Internal CA for certificate generation |
| **Storage** | ~50 GB for images, ~100 GB for Ollama models |

### Build Environment Requirements (Internet-Connected)

| Component | Requirement |
|-----------|-------------|
| **Docker** | 24+ |
| **Maven** | 3.9+ |
| **JDK** | 21 (Eclipse Temurin recommended) |
| **Node.js** | 20+ (for UI build) |
| **Disk space** | ~30 GB free for building and bundling |

---

## Step 1: Create the Image Bundle

Run this on a machine with internet access.

### 1.1 Build and Bundle All Images

```bash
# Clone the repository (or use an existing checkout)
git clone https://github.com/your-org/squadron.git
cd squadron

# Build and bundle all Squadron images
./deploy/airgap/bundle-images.sh

# Include infrastructure images (PostgreSQL, Redis, NATS, Keycloak, Ollama)
./deploy/airgap/bundle-images.sh --include-infra

# Use a specific version tag
./deploy/airgap/bundle-images.sh --tag v1.2.3 --include-infra
```

### 1.2 Bundle with Custom Registry

If the air-gapped environment uses a different registry address:

```bash
REGISTRY=harbor.internal.corp:443/squadron \
  ./deploy/airgap/bundle-images.sh --tag v1.2.3 --include-infra
```

### 1.3 Bundle Output

The script creates:

```
squadron-airgap-bundle/
├── squadron-images.tar.gz    # All Docker images (~5-10 GB)
├── values-airgap.yaml        # Helm values override
├── load-images.sh            # Image loader script
└── MANIFEST.txt              # Bundle contents manifest
```

### 1.4 Include Ollama Models

Ollama models must be bundled separately due to their size:

```bash
# Pull the required models
ollama pull llama3.1:70b
ollama pull nomic-embed-text

# Export models (Ollama stores models in ~/.ollama/models)
tar -czf ollama-models.tar.gz -C ~/.ollama models/
```

### 1.5 Package the Helm Chart

```bash
# Package the Helm chart for offline installation
helm package deploy/helm/squadron -d squadron-airgap-bundle/
```

---

## Step 2: Transfer the Bundle

### Option A: USB / External Drive

```bash
# Copy the bundle to a USB drive
cp -r squadron-airgap-bundle/ /mnt/usb/
cp ollama-models.tar.gz /mnt/usb/

# Verify checksums
sha256sum squadron-airgap-bundle/squadron-images.tar.gz > /mnt/usb/checksums.sha256
```

### Option B: Data Diode / One-Way Transfer

Follow your organization's data diode transfer procedures. Ensure the complete
bundle directory is transferred, including:
- `squadron-images.tar.gz`
- `values-airgap.yaml`
- `load-images.sh`
- `ollama-models.tar.gz`
- Helm chart package (`.tgz`)

### Verify Transfer Integrity

On the air-gapped side:

```bash
# Verify checksums
sha256sum -c checksums.sha256
```

---

## Step 3: Load Images in the Air-Gapped Environment

### 3.1 Load Images into Docker

```bash
cd /path/to/squadron-airgap-bundle

# Load images from the archive
./load-images.sh --archive squadron-images.tar.gz

# Verify all images are present
./load-images.sh --verify-only
```

### 3.2 Push to Internal Registry

```bash
# Push all loaded images to the internal registry
./load-images.sh --push --registry registry.internal:5000

# Or with a custom registry
./load-images.sh --push --registry harbor.internal.corp:443/squadron
```

### 3.3 Load Ollama Models

```bash
# Extract models to the Ollama data directory
tar -xzf ollama-models.tar.gz -C /var/lib/ollama/

# Or copy to the Kubernetes PersistentVolume
kubectl cp ollama-models.tar.gz squadron/ollama-0:/tmp/
kubectl exec -n squadron ollama-0 -- tar -xzf /tmp/ollama-models.tar.gz -C /root/.ollama/
```

---

## Step 4: Configure Internal DNS

Squadron services need to resolve each other and infrastructure components. Configure
your internal DNS server with the following entries, or use Kubernetes CoreDNS.

### Required DNS Entries

| Hostname | Purpose |
|----------|---------|
| `squadron.internal` | Main Squadron UI and API |
| `registry.internal` | Container registry |
| `postgres.squadron.internal` | PostgreSQL database |
| `redis.squadron.internal` | Redis cache |
| `nats.squadron.internal` | NATS message broker |
| `keycloak.squadron.internal` | Identity provider |
| `ollama.squadron.internal` | Local AI/LLM server |

### Example CoreDNS ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: coredns-custom
  namespace: kube-system
data:
  squadron.server: |
    squadron.internal:53 {
        hosts {
            10.0.1.10 squadron.internal
            10.0.1.11 registry.internal
            10.0.1.20 postgres.squadron.internal
            10.0.1.21 redis.squadron.internal
            10.0.1.22 nats.squadron.internal
            10.0.1.23 keycloak.squadron.internal
            10.0.1.24 ollama.squadron.internal
            fallthrough
        }
    }
```

### Alternative: /etc/hosts (Non-Kubernetes)

```
10.0.1.10  squadron.internal
10.0.1.11  registry.internal
10.0.1.20  postgres.squadron.internal
10.0.1.21  redis.squadron.internal
10.0.1.22  nats.squadron.internal
10.0.1.23  keycloak.squadron.internal
10.0.1.24  ollama.squadron.internal
```

---

## Step 5: Deploy with Helm

### 5.1 Configure TLS

Create a TLS secret with your internal CA certificate:

```bash
# Create the namespace
kubectl create namespace squadron

# Create TLS secret from internal CA
kubectl create secret tls squadron-tls \
  --cert=internal-ca-cert.pem \
  --key=internal-ca-key.pem \
  -n squadron

# Create CA bundle secret for service-to-service TLS
kubectl create secret generic squadron-ca-bundle \
  --from-file=ca.pem=internal-ca-cert.pem \
  -n squadron
```

### 5.2 Create Database Secrets

```bash
kubectl create secret generic squadron-db-credentials \
  --from-literal=username=squadron \
  --from-literal=password='<your-db-password>' \
  -n squadron

kubectl create secret generic squadron-redis-credentials \
  --from-literal=password='<your-redis-password>' \
  -n squadron

kubectl create secret generic squadron-encryption-key \
  --from-literal=key='<your-encryption-key>' \
  -n squadron
```

### 5.3 Install Squadron

```bash
# Install with the air-gap values override
helm install squadron ./deploy/helm/squadron \
  -f deploy/airgap/values-airgap.yaml \
  -n squadron \
  --create-namespace

# Or if using a packaged chart
helm install squadron ./squadron-0.1.0.tgz \
  -f values-airgap.yaml \
  -n squadron \
  --create-namespace
```

### 5.4 Customize Registry Address

If your internal registry is not `registry.internal:5000`, override it:

```bash
helm install squadron ./deploy/helm/squadron \
  -f deploy/airgap/values-airgap.yaml \
  --set global.imageRegistry=harbor.internal.corp:443 \
  -n squadron
```

### 5.5 Verify Deployment

```bash
# Check all pods are running
kubectl get pods -n squadron

# Check service endpoints
kubectl get svc -n squadron

# Verify the UI is accessible
curl -k https://squadron.internal/

# Check AI agent connectivity to Ollama
kubectl exec -n squadron deploy/squadron-agent -- \
  curl -s http://ollama.squadron.internal:11434/api/tags
```

---

## Step 6: Post-Deployment Configuration

### 6.1 Configure Keycloak

1. Access Keycloak at `https://keycloak.squadron.internal`
2. Create the `squadron` realm (or import the realm configuration)
3. Create client applications for Squadron services
4. Configure user federation (LDAP/AD if available internally)

### 6.2 Configure Platform Adapters

In air-gapped environments, Squadron typically connects to internal instances of:
- **GitLab** (self-hosted)
- **Jira Server/Data Center** (self-hosted)
- **Azure DevOps Server** (on-premises)

Configure these in the Squadron admin settings after deployment.

### 6.3 Configure Ollama Models

Verify the AI models are loaded and functional:

```bash
# Check available models
curl http://ollama.squadron.internal:11434/api/tags

# Test a model
curl http://ollama.squadron.internal:11434/api/generate \
  -d '{"model": "llama3.1:70b", "prompt": "Hello", "stream": false}'
```

---

## Updating Images in an Air-Gap Environment

### Incremental Updates

For updates, you only need to bundle the changed images:

```bash
# On the build machine (with internet)
# Build only the changed services
./deploy/airgap/bundle-images.sh --tag v1.2.4

# Transfer the new bundle to the air-gapped environment
# Then load and push:
./load-images.sh --archive squadron-images.tar.gz --push

# Update the Helm release
helm upgrade squadron ./deploy/helm/squadron \
  -f deploy/airgap/values-airgap.yaml \
  --set global.imageTag=v1.2.4 \
  -n squadron
```

### Full Re-deployment

For major version upgrades:

```bash
# 1. Create a full bundle with all images
./deploy/airgap/bundle-images.sh --tag v2.0.0 --include-infra

# 2. Transfer to air-gapped environment

# 3. Load and push all images
./load-images.sh --archive squadron-images.tar.gz --push

# 4. Upgrade Helm release
helm upgrade squadron ./deploy/helm/squadron \
  -f deploy/airgap/values-airgap.yaml \
  --set global.imageTag=v2.0.0 \
  -n squadron

# 5. Verify all pods are running with new images
kubectl get pods -n squadron -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.containers[*].image}{"\n"}{end}'
```

### Rolling Back

```bash
# List Helm release history
helm history squadron -n squadron

# Rollback to a previous revision
helm rollback squadron <revision-number> -n squadron
```

---

## Troubleshooting

### Images Fail to Pull

```bash
# Verify the image exists in the registry
curl -s http://registry.internal:5000/v2/_catalog | python3 -m json.tool

# Verify a specific image tag
curl -s http://registry.internal:5000/v2/squadron/squadron-gateway/tags/list

# Check Kubernetes events for pull errors
kubectl describe pod <pod-name> -n squadron
```

### Registry Certificate Issues

If using HTTPS with a self-signed certificate:

```bash
# Add the CA to Docker's trusted certificates
mkdir -p /etc/docker/certs.d/registry.internal:5000
cp internal-ca.pem /etc/docker/certs.d/registry.internal:5000/ca.crt

# Restart Docker
systemctl restart docker

# For containerd (Kubernetes)
# Add to /etc/containerd/config.toml:
# [plugins."io.containerd.grpc.v1.cri".registry.configs."registry.internal:5000".tls]
#   ca_file = "/etc/ssl/certs/internal-ca.pem"
```

### DNS Resolution Failures

```bash
# Test DNS from within a pod
kubectl run -it --rm debug --image=busybox -n squadron -- nslookup registry.internal

# Check CoreDNS logs
kubectl logs -n kube-system -l k8s-app=kube-dns
```

### Ollama Model Issues

```bash
# Check Ollama logs
kubectl logs -n squadron deploy/ollama

# Verify GPU access
kubectl exec -n squadron deploy/ollama -- nvidia-smi

# Re-pull a model (from local storage, not internet)
kubectl exec -n squadron deploy/ollama -- ollama pull llama3.1:70b
```

### Insufficient Resources

Air-gap deployments often run on resource-constrained environments. Minimum
recommended resources:

| Component | CPU | Memory | Storage |
|-----------|-----|--------|---------|
| Squadron services (11 total) | 8 cores | 16 GB | 10 GB |
| PostgreSQL | 4 cores | 8 GB | 50 GB |
| Redis | 2 cores | 4 GB | 2 GB |
| NATS | 2 cores | 2 GB | 10 GB |
| Keycloak | 2 cores | 4 GB | 2 GB |
| Ollama (with 70B model) | 8 cores | 48 GB | 100 GB |
| Container Registry | 2 cores | 4 GB | 50 GB |
| **Total** | **28 cores** | **86 GB** | **224 GB** |

For smaller environments, use a lighter Ollama model (e.g., `llama3.1:8b`) which
requires significantly less memory (~8 GB vs ~48 GB).
