# Squadron On-Premise Deployment Guide

## 1. Overview

This guide covers the deployment of Squadron on an enterprise-managed Kubernetes cluster.
It is intended for platform engineers deploying in environments where cloud-managed
services are unavailable or prohibited by policy.

Squadron consists of 10 backend Java microservices, 1 Angular frontend, and 4
infrastructure components (PostgreSQL, Redis, NATS, Keycloak). Deployment is managed via
the Helm umbrella chart at `deploy/helm/squadron/`.

---

## 2. Prerequisites

### Software Requirements

| Tool | Minimum Version | Purpose |
|------|----------------|---------|
| Kubernetes | 1.28+ | Container orchestration |
| Helm | 3.14+ | Chart-based deployment |
| kubectl | matching cluster | Cluster management |
| cert-manager | 1.14+ | TLS certificate lifecycle |
| HashiCorp Vault | 1.15+ | PKI and secret management |
| Maven | 3.9.x | Building Java modules |
| Java | 21 LTS | Compile target |
| Docker / Podman | 24+ / 4+ | Image builds |
| Node.js | 22 LTS | Angular frontend build |

An ingress controller (NGINX recommended) and a persistent storage provisioner (Longhorn,
OpenEBS, or Rook-Ceph for bare metal) are also required.

### Minimum Cluster Sizing

| Profile | Nodes | CPU | RAM | Storage |
|---------|-------|-----|-----|---------|
| **Small** (dev/staging, <50 users) | 4 | 32 cores | 64 GB | 500 GB |
| **Medium** (production, 50-500 users) | 8 | 64 cores | 128 GB | 1 TB |
| **Large** (production, 500+ users) | 12+ | 128 cores | 256 GB | 2 TB |

### Network Requirements

- Cluster nodes must reach the internal container registry
- Outbound HTTPS (443) required for LLM API endpoints (configurable per tenant)
- Gateway exposes port **8443** via LoadBalancer or NodePort

---

## 3. Architecture Overview

### Namespace Strategy

```
squadron              # All microservices and infrastructure
squadron-workspaces   # Ephemeral sandbox containers spawned by workspace service
squadron-infra        # Optional: separate namespace for shared infra
```

The default Helm chart deploys everything into `squadron`.

### Service Map

```
                     External Traffic
                           |
                     [LoadBalancer:8443]
                           |
                  +--------v--------+
                  |     Gateway     |  :8443
                  +--------+--------+
                           |
      +----------+---------+---------+----------+
      |          |         |         |          |
  +---v---+ +---v----+ +--v---+ +--v--+ +-----v------+
  |Identiy| |Orchestr| |Agent | | Git | |Notification|
  | :8081 | | :8083  | |:8085 | |:8087| |   :8089    |
  +-------+ +---+----+ +--+---+ +-----+ +------------+
                |          |
           +----v---+  +---v-----+  +------+  +------+
           |Platform|  |Workspace|  |Review|  |Config|
           | :8084  |  | :8086   |  |:8088 |  |:8082 |
           +--------+  +---------+  +------+  +------+

  Infrastructure: PostgreSQL:5432 | NATS:4222 | Redis:6379 | Keycloak:8080
```

### Communication Patterns

- **Sync:** OpenFeign (agent -> orchestrator/git/review/workspace; orchestrator -> platform)
- **Async:** NATS JetStream for event-driven inter-service messaging
- **Real-time:** STOMP over WebSocket through gateway for frontend push

---

## 4. Cluster Preparation

### 4.1 Install cert-manager

```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.5/cert-manager.yaml
kubectl -n cert-manager wait --for=condition=Ready pod --all --timeout=120s
```

### 4.2 Create Namespaces

```bash
kubectl apply -f deploy/k8s/namespace.yaml

# Or manually:
kubectl create namespace squadron
kubectl create namespace squadron-workspaces
kubectl label namespace squadron app.kubernetes.io/part-of=squadron
```

### 4.3 Storage Class

```bash
kubectl get storageclass
# If using a non-default class, set: global.storageClass: "longhorn"
```

### 4.4 Image Pull Secrets

```bash
kubectl -n squadron create secret docker-registry squadron-registry \
  --docker-server=registry.internal.example.com \
  --docker-username=squadron \
  --docker-password='<password>'
```

```yaml
global:
  imageRegistry: registry.internal.example.com
  imagePullSecrets:
    - name: squadron-registry
```

---

## 5. Infrastructure Deployment

### 5.1 PostgreSQL

Squadron uses **database-per-service**. Required databases:

`squadron_identity`, `squadron_config`, `squadron_orchestrator`, `squadron_platform`,
`squadron_agent`, `squadron_workspace`, `squadron_git`, `squadron_review`,
`squadron_notification`, `keycloak`

**Option A: Bundled StatefulSet** -- the Helm chart includes a PostgreSQL StatefulSet:

```yaml
postgresql:
  storage: 200Gi
  resources:
    requests: { cpu: "2", memory: 4Gi }
    limits:   { cpu: "4", memory: 8Gi }
```

**Option B: CloudNativePG** (recommended for production HA):

```bash
kubectl apply -f https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/main/releases/cnpg-1.22.0.yaml
```

```yaml
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata:
  name: squadron-pg
  namespace: squadron
spec:
  instances: 3
  postgresql:
    parameters:
      max_connections: "200"
      shared_buffers: "2GB"
  storage:
    size: 200Gi
  bootstrap:
    initdb:
      database: squadron_identity
      owner: squadron
      postInitSQL:
        - CREATE DATABASE squadron_config OWNER squadron;
        - CREATE DATABASE squadron_orchestrator OWNER squadron;
        - CREATE DATABASE squadron_platform OWNER squadron;
        - CREATE DATABASE squadron_agent OWNER squadron;
        - CREATE DATABASE squadron_workspace OWNER squadron;
        - CREATE DATABASE squadron_git OWNER squadron;
        - CREATE DATABASE squadron_review OWNER squadron;
        - CREATE DATABASE squadron_notification OWNER squadron;
        - CREATE DATABASE keycloak OWNER squadron;
```

**Option C: External PostgreSQL** -- set `postgresql.enabled: false` and override
`SPRING_DATASOURCE_URL` per service.

### 5.2 Redis

Used by the gateway for rate limiting and session caching.

```yaml
redis:
  storage: 20Gi
  resources:
    requests: { cpu: 500m, memory: 512Mi }
    limits:   { cpu: "1", memory: 1Gi }
```

For HA, use the Bitnami Redis chart with `architecture=replication`.

### 5.3 NATS with JetStream

JetStream must be enabled for durable message delivery.

```yaml
nats:
  jetstream: { enabled: true, storage: 50Gi }
  resources:
    requests: { cpu: 500m, memory: 512Mi }
    limits:   { cpu: "1", memory: 1Gi }
```

For HA, use the official NATS Helm chart with `config.cluster.replicas=3`.

### 5.4 Keycloak

```yaml
keycloak:
  adminPassword: ""  # Must be set at deploy time
  resources:
    requests: { cpu: "1", memory: 1Gi }
    limits:   { cpu: "2", memory: 2Gi }
```

To use an existing Keycloak, set `keycloak.enabled: false` and configure
`KEYCLOAK_AUTH_SERVER_URL` on the identity service.

---

## 6. Building and Pushing Images

```bash
# Build all Java modules
mvn clean package -DskipTests -q

# Build and push container images
export REGISTRY=registry.internal.example.com TAG=0.1.0
SERVICES="gateway identity config orchestrator platform agent workspace git review notification"

for svc in $SERVICES; do
  docker build -t "${REGISTRY}/squadron/${svc}:${TAG}" -f "squadron-${svc}/Dockerfile" "squadron-${svc}/"
  docker push "${REGISTRY}/squadron/${svc}:${TAG}"
done

# Angular frontend
docker build -t "${REGISTRY}/squadron/ui:${TAG}" -f squadron-ui/Dockerfile squadron-ui/
docker push "${REGISTRY}/squadron/ui:${TAG}"
```

Update Helm values with `global.imageRegistry` and per-service `image.tag`.

---

## 7. Deploying Squadron

### 7.1 Create Secrets

```bash
kubectl -n squadron create secret generic squadron-postgresql-credentials \
  --from-literal=SPRING_DATASOURCE_USERNAME=squadron \
  --from-literal=SPRING_DATASOURCE_PASSWORD="$(openssl rand -base64 32)"

kubectl -n squadron create secret generic squadron-keycloak-credentials \
  --from-literal=KEYCLOAK_ADMIN=admin \
  --from-literal=KEYCLOAK_ADMIN_PASSWORD="$(openssl rand -base64 32)"

kubectl -n squadron create secret generic squadron-encryption-key \
  --from-literal=ENCRYPTION_KEY="$(openssl rand -hex 32)"

kubectl -n squadron create secret generic squadron-jwt-key \
  --from-literal=JWT_SIGNING_KEY="$(openssl rand -base64 64)"
```

### 7.2 Helm Install

```bash
helm upgrade --install squadron deploy/helm/squadron \
  --namespace squadron --create-namespace \
  -f deploy/helm/squadron/values-prod.yaml \
  --set global.imageRegistry=registry.internal.example.com \
  --set postgresql.password="$(kubectl -n squadron get secret squadron-postgresql-credentials \
    -o jsonpath='{.data.SPRING_DATASOURCE_PASSWORD}' | base64 -d)" \
  --set keycloak.adminPassword="$(kubectl -n squadron get secret squadron-keycloak-credentials \
    -o jsonpath='{.data.KEYCLOAK_ADMIN_PASSWORD}' | base64 -d)" \
  --timeout 10m --wait
```

### 7.3 Verify

```bash
kubectl -n squadron get pods            # All should be Running
kubectl -n squadron get svc             # Check endpoints
kubectl -n squadron port-forward svc/squadron-gateway 8443:8443 &
curl -k https://localhost:8443/actuator/health
```

### 7.4 Service Port Reference

| Service | Port | Service | Port |
|---------|------|---------|------|
| Gateway | 8443 | Agent | 8085 |
| Identity | 8081 | Workspace | 8086 |
| Config | 8082 | Git | 8087 |
| Orchestrator | 8083 | Review | 8088 |
| Platform | 8084 | Notification | 8089 |

All liveness probes: `/actuator/health/liveness` (initialDelay=60s).
All readiness probes: `/actuator/health/readiness` (initialDelay=30s).

---

## 8. mTLS Configuration

### 8.1 Vault PKI Setup

```bash
export VAULT_ADDR=https://vault.internal.example.com:8200
export VAULT_TOKEN=<root-or-admin-token>
./deploy/vault/setup-pki.sh --domain squadron.local
```

This creates: Root CA at `pki/` (EC P-384, 10y), Intermediate CA at `pki_int/` (EC P-256,
5y), roles `squadron-service` and `squadron-infra`, Vault policy `squadron-cert-manager`,
and Kubernetes auth role `cert-manager`.

### 8.2 Apply ClusterIssuers and Certificates

```bash
kubectl apply -f deploy/vault/cert-manager/cluster-issuer.yaml
kubectl apply -f deploy/vault/cert-manager/certificates.yaml
```

Two issuers are created:

| Issuer | Vault Path | Use |
|--------|-----------|-----|
| `squadron-vault-issuer` | `pki_int/sign/squadron-service` | App certs (720h, ECDSA P-256) |
| `squadron-vault-infra-issuer` | `pki_int/sign/squadron-infra` | Infra certs (2160h, ECDSA P-256) |

Verify: `kubectl -n squadron get certificates`

### 8.3 Deploy with mTLS Overlay

```bash
helm upgrade --install squadron deploy/helm/squadron \
  --namespace squadron \
  -f deploy/helm/squadron/values-prod.yaml \
  -f deploy/vault/values-mtls.yaml \
  --set global.imageRegistry=registry.internal.example.com \
  --timeout 10m --wait
```

The overlay mounts TLS secrets at `/etc/tls` (tls.crt, tls.key, ca.crt), enables Spring
Boot HTTPS with PEM keystores, requires client cert auth, sets PostgreSQL
`sslmode=verify-full`, uses `tls://` NATS URLs, and enables Redis SSL.

---

## 9. Network Policies

The Helm chart enforces **default-deny ingress** with explicit allow rules:

| Policy | Allows | Ports |
|--------|--------|-------|
| `squadron-default-deny` | Nothing (deny all) | -- |
| `squadron-allow-gateway-to-backends` | Gateway -> all backends | all |
| `squadron-allow-backends-to-postgresql` | All pods -> PostgreSQL | 5432 |
| `squadron-allow-backends-to-nats` | All pods -> NATS | 4222, 8222 |
| `squadron-allow-gateway-to-redis` | Gateway -> Redis | 6379 |
| `squadron-allow-agent-feign` | Agent -> Orchestrator, Git, Review, Workspace | all |
| `squadron-allow-orchestrator-to-platform` | Orchestrator -> Platform | all |
| `squadron-allow-identity-keycloak` | Identity -> Keycloak | 8080 |
| `squadron-allow-external-to-gateway` | Any -> Gateway | 8443 |

Verify: `kubectl -n squadron get networkpolicies`

To allow Prometheus scraping, add a supplementary NetworkPolicy permitting ingress from
the `monitoring` namespace on the service port.

---

## 10. Monitoring and Observability

### 10.1 Prometheus

The Helm chart includes a ServiceMonitor scraping `/actuator/prometheus` every 30s.
Requires kube-prometheus-stack:

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install kube-prometheus prometheus-community/kube-prometheus-stack -n monitoring --create-namespace
```

Key metrics: `http_server_requests_seconds_*`, `jvm_memory_used_bytes`,
`hikaricp_connections_active`, `process_cpu_usage`.

### 10.2 Alerting

```yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: squadron-alerts
  namespace: squadron
spec:
  groups:
    - name: squadron.rules
      rules:
        - alert: SquadronServiceDown
          expr: up{job=~"squadron-.*"} == 0
          for: 2m
          labels: { severity: critical }
        - alert: HighErrorRate
          expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1
          for: 5m
          labels: { severity: warning }
        - alert: DatabasePoolExhausted
          expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
          for: 5m
          labels: { severity: critical }
```

### 10.3 Log Aggregation

Services write structured JSON to stdout. Deploy Loki + Promtail or your preferred stack.

---

## 11. Scaling

### 11.1 HPA Configuration

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: squadron-agent-hpa
  namespace: squadron
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: squadron-agent
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target: { type: Utilization, averageUtilization: 70 }
```

### 11.2 Recommended Profiles

| Service | Min | Max | Trigger | Service | Min | Max | Trigger |
|---------|-----|-----|---------|---------|-----|-----|---------|
| Gateway | 3 | 8 | CPU 70% | Agent | 3 | 10 | CPU 70% |
| Orchestrator | 3 | 6 | CPU 75% | Workspace | 2 | 8 | CPU 70% |
| Git | 2 | 6 | CPU 75% | Review | 2 | 6 | CPU 75% |
| Identity | 2 | 4 | CPU 80% | Platform | 2 | 4 | CPU 80% |
| Config | 2 | 3 | CPU 80% | Notification | 2 | 4 | CPU 80% |

The **agent** service is the most resource-intensive (1-2 CPU, 2-4 GB RAM per pod).

### 11.3 PodDisruptionBudgets

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: squadron-gateway-pdb
  namespace: squadron
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app.kubernetes.io/name: gateway
```

Create PDBs for gateway, agent, and orchestrator at minimum.

---

## 12. Security Hardening

### 12.1 Pod Security Standards

```bash
kubectl label namespace squadron \
  pod-security.kubernetes.io/enforce=restricted \
  pod-security.kubernetes.io/audit=restricted \
  pod-security.kubernetes.io/warn=restricted
```

All containers should use: `runAsNonRoot: true`, `allowPrivilegeEscalation: false`,
`readOnlyRootFilesystem: true`, `capabilities.drop: [ALL]`, `seccompProfile: RuntimeDefault`.

### 12.2 Secret Management with Vault

Beyond PKI, store application secrets in Vault KV and inject via the Vault Agent sidecar
or Vault CSI provider instead of Kubernetes Secrets:

```bash
vault secrets enable -path=secret kv-v2
vault kv put secret/squadron/database username=squadron password="$(openssl rand -base64 32)"
vault kv put secret/squadron/jwt signing-key="$(openssl rand -base64 64)"
```

### 12.3 Image Scanning

```bash
# Trivy (fails on CRITICAL+HIGH)
trivy image --config deploy/security/trivy-config.yaml \
  registry.internal.example.com/squadron/gateway:0.1.0

# OWASP Dependency-Check (fails on CVSS >= 9)
mvn org.owasp:dependency-check-maven:check \
  -DsuppressionFile=deploy/security/dependency-check-suppression.xml
```

### 12.4 RBAC

Apply least-privilege roles per service account. The **workspace** service requires
additional permissions to manage pods in `squadron-workspaces`.

---

## 13. Backup and Recovery

### 13.1 PostgreSQL

```bash
# Logical backup of all databases
DATABASES="squadron_identity squadron_config squadron_orchestrator squadron_platform \
  squadron_agent squadron_workspace squadron_git squadron_review squadron_notification keycloak"

for db in $DATABASES; do
  kubectl -n squadron exec squadron-postgresql-0 -- \
    pg_dump -U squadron -Fc "$db" > "backup-${db}-$(date +%Y%m%d).dump"
done
```

For continuous backup, configure WAL archiving to S3-compatible storage. CloudNativePG
supports this natively via `spec.backup.barmanObjectStore`.

### 13.2 NATS and Redis

```bash
# NATS: export JetStream streams
kubectl -n squadron exec squadron-nats-0 -- nats stream backup <stream-name> /tmp/backup

# Redis: trigger RDB snapshot
kubectl -n squadron exec squadron-redis-0 -- redis-cli BGSAVE
kubectl -n squadron cp squadron-redis-0:/data/dump.rdb ./redis-backup.rdb
```

### 13.3 Disaster Recovery

1. Restore PostgreSQL from pg_dump or WAL backup
2. Restore NATS streams (or let services re-publish)
3. Redeploy via Helm with the same values
4. Verify health endpoints
5. Redis caches will re-warm automatically

```bash
kubectl -n squadron exec -i squadron-postgresql-0 -- \
  pg_restore -U squadron -d squadron_orchestrator --clean --if-exists < backup.dump
```

---

## 14. Upgrading

### 14.1 Procedure

1. Read release notes for breaking changes
2. Back up all PostgreSQL databases (Section 13.1)
3. Push new images to the internal registry
4. Run a dry-run upgrade, then apply:

```bash
helm upgrade squadron deploy/helm/squadron \
  --namespace squadron \
  -f deploy/helm/squadron/values-prod.yaml \
  --set global.imageRegistry=registry.internal.example.com \
  --dry-run --debug

helm upgrade squadron deploy/helm/squadron \
  --namespace squadron \
  -f deploy/helm/squadron/values-prod.yaml \
  --set global.imageRegistry=registry.internal.example.com \
  --timeout 10m --wait
```

### 14.2 Rollback

```bash
helm history squadron -n squadron
helm rollback squadron <revision> -n squadron --wait
```

### 14.3 Database Migrations

Flyway migrations run automatically on startup from
`src/main/resources/db/migration/V<version>__<description>.sql`. If a migration fails,
check `flyway_schema_history`, fix the SQL, run `flyway repair`, and restart the service.

---

## 15. Troubleshooting

### ImagePullBackOff

```bash
kubectl -n squadron describe pod <pod-name> | grep -A5 Events
# Causes: wrong tag, missing imagePullSecrets, unreachable registry
```

### CrashLoopBackOff

```bash
kubectl -n squadron logs <pod-name> --previous
# Check for OOMKilled:
kubectl -n squadron get pod <pod-name> -o jsonpath='{.status.containerStatuses[0].lastState.terminated.reason}'
# Causes: DB unreachable, missing secrets, insufficient memory
```

### Database Connection Failures

```bash
kubectl -n squadron get pods -l app.kubernetes.io/name=postgresql
kubectl -n squadron port-forward <pod-name> 8085:8085 &
curl -s http://localhost:8085/actuator/health | jq '.components.db'
# Causes: password mismatch, missing database, network policy blocking 5432, max_connections exhausted
```

### Certificate Expiry

```bash
kubectl -n squadron get certificates
kubectl -n cert-manager logs -l app=cert-manager
# Force renewal:
kubectl -n squadron delete secret squadron-gateway-tls  # cert-manager re-issues automatically
# Causes: Vault unreachable, cert-manager down, ClusterIssuer misconfigured
```

### NATS Connectivity

```bash
kubectl -n squadron exec squadron-nats-0 -- nats server info
kubectl -n squadron exec squadron-nats-0 -- nats stream ls
# Causes: PVC not bound, JetStream storage full, network policy blocking 4222, TLS URL mismatch
```

### Health Check Failures

```bash
kubectl -n squadron logs <pod-name> | grep "Started"
kubectl -n squadron port-forward <pod-name> 8083:8083 &
curl -s http://localhost:8083/actuator/health/readiness | jq .
# If slow to start, increase initialDelaySeconds (liveness: 120, readiness: 60)
```

### Helm Failures

```bash
helm status squadron -n squadron
helm list -n squadron --all
# If stuck in failed state:
helm uninstall squadron -n squadron  # PVCs are retained by default
```
