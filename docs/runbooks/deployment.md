# Deployment Runbook

## Prerequisites

- Docker 27+ and Docker Compose v2
- For production: Kubernetes 1.31+, Helm 3.x
- Java 21 (for building from source)
- Maven 3.9.x (for building from source)
- Node.js 22.x (for building the Angular frontend)
- At least 16 GB RAM for running the full stack locally
- At least 8 GB RAM for infrastructure-only

## Development Deployment (Docker Compose)

### Quick Start

```bash
# Clone the repository
git clone https://github.com/your-org/squadron.git
cd squadron

# Build all backend services
mvn clean package -DskipTests

# Build the Angular frontend
cd squadron-ui
npm install
npx ng build --configuration=production
cd ..

# Start infrastructure only (PostgreSQL, Redis, NATS, Keycloak, Mailpit, Ollama)
cd deploy/docker
docker compose up -d

# Start infrastructure + all backend services
docker compose --profile services up -d

# Start everything including the frontend
docker compose --profile services --profile frontend up -d

# Check status
docker compose ps
```

### Infrastructure Services

| Service | Port | Purpose | Health Check |
|---|---|---|---|
| PostgreSQL | 5432 | Primary database | `pg_isready -U squadron` |
| PgBouncer | 6432 | Connection pooler | TCP connect |
| Redis | 6379 | Cache, sessions, locks | `redis-cli ping` |
| NATS | 4222 (client), 8222 (monitor) | Message broker | HTTP GET :8222/healthz |
| Keycloak | 8080 | Identity provider | HTTP GET :8080/health/ready |
| Mailpit | 1025 (SMTP), 8025 (Web) | Dev email testing | HTTP GET :8025 |
| Ollama | 11434 | Local AI model server | HTTP GET :11434/api/tags |

### Service Startup Order

Services depend on infrastructure being healthy. Docker Compose handles this via
`depends_on` with health checks. The startup order is:

1. PostgreSQL (with health check)
2. PgBouncer, Redis, NATS, Keycloak, Mailpit, Ollama (depend on PostgreSQL)
3. Backend services (depend on PostgreSQL + NATS)
4. Frontend (depends on API Gateway)

### Database Initialization

The `deploy/docker/init-databases.sql` script creates all service databases:

```
squadron_identity, squadron_config, squadron_orchestrator,
squadron_platform, squadron_agent, squadron_workspace,
squadron_git, squadron_review, squadron_notification
```

Flyway migrations run automatically on service startup.

### Environment Variables

Common environment variables (set in `docker-compose.yml`):

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `docker` | Spring profile |
| `NATS_URL` | `nats://nats:4222` | NATS connection URL |
| `SPRING_DATASOURCE_USERNAME` | `squadron` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `squadron` | DB password |
| `KEYCLOAK_URL` | `http://keycloak:8080` | Keycloak URL |
| `OPENAI_API_KEY` | (none) | OpenAI API key (for cloud AI) |
| `OPENAI_BASE_URL` | `https://api.openai.com` | OpenAI endpoint |
| `OPENAI_MODEL` | `gpt-4o` | Default OpenAI model |

### Stopping Services

```bash
# Stop all services but keep data volumes
docker compose --profile services --profile frontend down

# Stop and remove all data (destructive)
docker compose --profile services --profile frontend down -v
```

## Production Deployment (Helm)

### Cluster Preparation

```bash
# Create namespaces
kubectl create namespace squadron-system
kubectl create namespace squadron-workspaces
kubectl create namespace squadron-infra

# Install cert-manager (for TLS certificate management)
helm repo add jetstack https://charts.jetstack.io
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --set installCRDs=true

# Install NGINX Ingress Controller
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace
```

### Deploy Infrastructure

```bash
# Deploy PostgreSQL (using Bitnami chart or CloudNativePG operator)
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install postgresql bitnami/postgresql \
  --namespace squadron-infra \
  --set auth.postgresPassword=<password> \
  --set primary.persistence.size=100Gi

# Deploy Redis
helm install redis bitnami/redis \
  --namespace squadron-infra \
  --set auth.enabled=true \
  --set auth.password=<password>

# Deploy NATS
helm repo add nats https://nats-io.github.io/k8s/helm/charts/
helm install nats nats/nats \
  --namespace squadron-infra \
  --set config.jetstream.enabled=true \
  --set config.jetstream.fileStore.pvc.size=50Gi

# Deploy Keycloak
helm repo add codecentric https://codecentric.github.io/helm-charts
helm install keycloak codecentric/keycloakx \
  --namespace squadron-infra \
  --set database.vendor=postgres \
  --set database.hostname=postgresql.squadron-infra.svc
```

### Deploy Squadron Services

```bash
# Add the Squadron Helm repository (or use local charts)
cd deploy/helm

# Deploy the umbrella chart
helm install squadron . \
  --namespace squadron-system \
  --values values-production.yaml \
  --set global.imageRegistry=your-registry.example.com \
  --set global.postgresql.host=postgresql.squadron-infra.svc \
  --set global.redis.host=redis-master.squadron-infra.svc \
  --set global.nats.url=nats://nats.squadron-infra.svc:4222 \
  --set global.keycloak.url=https://keycloak.squadron-infra.svc
```

### Helm Values Reference

Key values in `values-production.yaml`:

```yaml
global:
  imageRegistry: registry.example.com
  imageTag: "0.1.0"
  postgresql:
    host: postgresql.squadron-infra.svc
    port: 5432
    username: squadron
    existingSecret: squadron-db-credentials
  redis:
    host: redis-master.squadron-infra.svc
    port: 6379
    existingSecret: squadron-redis-credentials
  nats:
    url: nats://nats.squadron-infra.svc:4222
  keycloak:
    url: https://keycloak.example.com
  vault:
    enabled: true
    address: https://vault.squadron-infra.svc:8200

services:
  gateway:
    replicas: 2
    resources:
      requests: { cpu: 500m, memory: 512Mi }
      limits: { cpu: 1000m, memory: 1Gi }
  orchestrator:
    replicas: 2
    resources:
      requests: { cpu: 500m, memory: 512Mi }
      limits: { cpu: 1000m, memory: 1Gi }
  agent:
    replicas: 3
    resources:
      requests: { cpu: 1000m, memory: 1Gi }
      limits: { cpu: 2000m, memory: 2Gi }
  # ... similar for other services

ingress:
  enabled: true
  className: nginx
  host: squadron.example.com
  tls:
    enabled: true
    secretName: squadron-tls
```

### Upgrading

```bash
# Rolling upgrade
helm upgrade squadron . \
  --namespace squadron-system \
  --values values-production.yaml \
  --set global.imageTag="0.2.0"

# Check rollout status
kubectl -n squadron-system rollout status deployment/squadron-gateway
kubectl -n squadron-system rollout status deployment/squadron-orchestrator
# ... repeat for all services

# Rollback if needed
helm rollback squadron 1 --namespace squadron-system
```

## Health Check Endpoints

All Spring Boot services expose the following actuator endpoints:

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Overall health status |
| `GET /actuator/health/liveness` | Liveness probe (is the process alive?) |
| `GET /actuator/health/readiness` | Readiness probe (can it accept traffic?) |
| `GET /actuator/info` | Application info (version, build time) |
| `GET /actuator/prometheus` | Prometheus metrics |

### Checking Health

```bash
# Check individual service health
curl http://localhost:8081/actuator/health  # Identity
curl http://localhost:8082/actuator/health  # Config
curl http://localhost:8083/actuator/health  # Orchestrator
curl http://localhost:8084/actuator/health  # Platform
curl http://localhost:8085/actuator/health  # Agent
curl http://localhost:8086/actuator/health  # Workspace
curl http://localhost:8087/actuator/health  # Git
curl http://localhost:8088/actuator/health  # Review
curl http://localhost:8089/actuator/health  # Notification

# Check via API Gateway
curl https://localhost:8443/actuator/health

# Check NATS
curl http://localhost:8222/healthz

# Check Keycloak
curl http://localhost:8080/health/ready
```

## Common Deployment Issues

### Database Connection Failures

**Symptom**: Services fail to start with "Connection refused" or "FATAL: too many
connections".

**Solutions**:
1. Ensure PostgreSQL is healthy: `docker compose ps postgres`
2. Check that `init-databases.sql` ran successfully:
   `docker compose exec postgres psql -U squadron -c '\l'`
3. If connection limit exceeded, check PgBouncer stats:
   `docker compose exec pgbouncer pgbouncer -d /etc/pgbouncer/pgbouncer.ini`
4. Increase `max_connections` in PostgreSQL or `default_pool_size` in PgBouncer

### Flyway Migration Conflicts

**Symptom**: Service fails with "Migration checksum mismatch" or "out of order"
migration.

**Solutions**:
1. Never modify an existing migration file that has been applied
2. For development, reset: `docker compose down -v && docker compose up -d`
3. For production, use `flyway repair` carefully

### NATS Connection Issues

**Symptom**: Services log "NATS connection lost" or events not being delivered.

**Solutions**:
1. Check NATS health: `curl http://localhost:8222/healthz`
2. Check connections: `curl http://localhost:8222/connz`
3. Check JetStream: `curl http://localhost:8222/jsz`
4. Restart NATS if needed: `docker compose restart nats`

### Keycloak Startup Delay

**Symptom**: Services start before Keycloak is ready, JWT validation fails.

**Solutions**:
1. Keycloak has a long startup time (60s+ on first run)
2. Services should retry Keycloak connection on startup
3. Check Keycloak logs: `docker compose logs keycloak`
4. First run requires realm/client configuration

### Out of Memory (OOM)

**Symptom**: Containers killed with OOM, `docker compose ps` shows "Exit 137".

**Solutions**:
1. Increase Docker memory allocation (Docker Desktop: Settings > Resources)
2. Reduce service memory in `docker-compose.yml` `deploy.resources.limits`
3. Run only needed services: `docker compose up -d postgres redis nats keycloak`
4. Production: set proper HPA and resource requests/limits in Helm values
