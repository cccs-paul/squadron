# Self-Hosted Deployment Guide

## Overview

This guide walks through deploying the full Squadron stack on a single server or small
cluster using Docker Compose. It covers building from source, configuring infrastructure,
connecting external platforms, and hardening for production use.

**Target audience**: Small teams (2-20 developers) running a proof-of-concept, internal
deployment, or small production instance. For large-scale Kubernetes deployments, see the
[Helm deployment guide](../runbooks/deployment.md#production-deployment-helm).

**What gets deployed**:

- 7 infrastructure containers: PostgreSQL 17, PgBouncer, Redis 7, NATS 2 (JetStream),
  Keycloak 26, Mailpit, Ollama
- 10 backend Java microservices (Spring Boot 3.x on eclipse-temurin:21-jre-alpine)
- 1 Angular frontend (nginx:alpine)

All containers run on a single Docker bridge network (`squadron-net`) and communicate
over internal DNS. External access is through published host ports.

---

## Prerequisites

### Hardware Requirements

| Resource | Minimum | Recommended |
|---|---|---|
| CPU cores | 4 | 8+ |
| RAM | 16 GB | 32 GB |
| Disk | 50 GB free | 100 GB+ SSD |
| Network | Broadband (for pulling images + AI API calls) | Low-latency connection to AI provider |

The minimum spec is tight. Running all 18 containers at 16 GB RAM leaves little
headroom. If you plan to use Ollama with local models, add at least 8 GB RAM on top
(16 GB+ VRAM for GPU inference, or 24 GB+ system RAM for CPU inference).

### Software Requirements

| Software | Version | Purpose |
|---|---|---|
| Docker Engine | 27+ | Container runtime |
| Docker Compose | v2 (plugin) | Multi-container orchestration |
| Java (JDK) | 21 LTS | Building backend services |
| Maven | 3.9.x | Java build tool |
| Node.js | 22.x | Building the Angular frontend |
| npm | (bundled with Node) | Frontend dependency management |
| Git | 2.x | Cloning the repository |

Verify your environment:

```bash
docker --version          # Docker version 27.x.x
docker compose version    # Docker Compose version v2.x.x
java -version             # openjdk version "21.x.x"
mvn --version             # Apache Maven 3.9.x
node --version            # v22.x.x
git --version             # git version 2.x.x
```

### Operating System

Any Linux distribution with Docker support. Tested on Ubuntu 22.04/24.04, Debian 12,
and RHEL 9. macOS and Windows (via Docker Desktop) work for development but are not
recommended for production deployments.

---

## Quick Start

The fastest path from clone to running is the `testldap-build-and-start.sh` script.
It builds all Maven modules, compiles the Angular frontend, builds Docker images, and
starts the entire stack including a test LDAP server.

```bash
# 1. Clone the repository
git clone https://github.com/your-org/squadron.git
cd squadron

# 2. (Optional) Set your OpenAI API key for AI features
export OPENAI_API_KEY="sk-your-key-here"

# 3. Build and start everything
./testldap-build-and-start.sh
```

First run takes 10-20 minutes (Maven downloads, npm install, Docker image pulls).
Subsequent runs with `--skip-build` take under 2 minutes.

When the script completes, you'll see:

| Endpoint | URL |
|---|---|
| Squadron UI | http://localhost:4200 |
| API Gateway | http://localhost:8443 |
| Keycloak Admin | http://localhost:8080 (admin / admin) |
| Mailpit Web UI | http://localhost:8025 |
| NATS Monitoring | http://localhost:8222 |

### Quick Start Script Options

```bash
./testldap-build-and-start.sh --skip-build   # Restart without rebuilding
./testldap-build-and-start.sh --skip-tests   # Build without running tests
./testldap-build-and-start.sh --infra        # Infrastructure only (for local dev)
./testldap-build-and-start.sh --clean        # Wipe all data and start fresh
./testldap-build-and-start.sh --stop         # Stop all containers
./testldap-build-and-start.sh --status       # Show container status
./testldap-build-and-start.sh --logs         # Tail all logs
./testldap-build-and-start.sh --pull-model   # Pull the default Ollama model
```

---

## Step-by-Step Deployment

If you need more control than the quick-start script provides, follow these steps.

### Step 1: Build Backend Services

Build all Java modules with Maven. The parent POM at the repository root orchestrates
the multi-module build.

```bash
cd squadron

# Full build with tests (recommended for first run)
mvn clean package -q

# Or skip tests for a faster build
mvn clean package -q -DskipTests
```

This produces a fat JAR in each module's `target/` directory:

```
squadron-gateway/target/squadron-gateway-*.jar
squadron-identity/target/squadron-identity-*.jar
squadron-config/target/squadron-config-*.jar
...
```

### Step 2: Build the Angular Frontend

```bash
cd squadron-ui

# Install dependencies (first run only, or after package.json changes)
npm ci --silent

# Build the production bundle
npx ng build --configuration=production

cd ..
```

The build output lands in `squadron-ui/dist/squadron-ui/browser/`, which the Dockerfile
copies into an nginx container.

### Step 3: Build Docker Images

Each service has a Dockerfile. Backend services use `eclipse-temurin:21-jre-alpine` as
the base image. The frontend uses `nginx:alpine`.

```bash
# Build all backend images
for module in gateway identity config orchestrator platform agent workspace git review notification; do
  docker build -t "squadron/squadron-${module}:latest" \
    -f "squadron-${module}/Dockerfile" .
done

# Build the frontend image
docker build -t "squadron/squadron-ui:latest" \
  -f squadron-ui/Dockerfile squadron-ui/
```

Verify images were created:

```bash
docker images | grep squadron/
```

### Step 4: Start Infrastructure

Start the infrastructure services first. These have no profile and start by default.

```bash
cd deploy/docker

# Start all infrastructure
docker compose up -d postgres redis nats keycloak mailpit pgbouncer ollama
```

### Step 5: Wait for Health Checks

Infrastructure services must be healthy before starting application services.
Docker Compose health checks handle this automatically via `depends_on`, but if you
are starting manually, wait for each service:

```bash
# PostgreSQL (fastest — usually ready in 5-10s)
until docker compose exec postgres pg_isready -U squadron; do sleep 2; done

# Redis
until docker compose exec redis redis-cli ping | grep -q PONG; do sleep 2; done

# NATS
until curl -sf http://localhost:8222/healthz; do sleep 2; done

# Keycloak (slowest — 60-120s on first start, creates database tables)
echo "Waiting for Keycloak (this takes 1-2 minutes on first start)..."
until curl -sf http://localhost:8080/health/ready; do sleep 5; done

# Ollama
until docker compose exec ollama ollama list 2>/dev/null; do sleep 3; done
```

### Step 6: Start Backend Services

```bash
docker compose --profile services up -d
```

This starts all 10 backend services. Each service waits for PostgreSQL and NATS via
`depends_on` conditions. Monitor startup:

```bash
# Watch service health in real time
watch -n 5 'docker compose --profile services ps'

# Or check health endpoints directly
for port in 8081 8082 8083 8084 8085 8086 8087 8088 8089 8443; do
  status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${port}/actuator/health" 2>/dev/null)
  echo "Port ${port}: ${status}"
done
```

All services should report healthy within 60-90 seconds. Keycloak-dependent operations
(login, token validation) may take longer on first start.

### Step 7: Start the Frontend

```bash
docker compose --profile frontend up -d
```

The frontend container depends on `squadron-gateway` being healthy. Once started, the
UI is available at http://localhost:4200.

### Full Single-Command Startup

After the first build, you can start everything with one command:

```bash
docker compose --profile services --profile frontend up -d
```

---

## Configuration

### Environment Variables

Configuration is passed via environment variables in `docker-compose.yml`. The
`x-common-env` YAML anchor defines shared variables inherited by all backend services.

#### Common Variables

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `docker` | Activates the Docker Spring profile |
| `NATS_URL` | `nats://nats:4222` | NATS server connection string |
| `SPRING_DATASOURCE_USERNAME` | `squadron` | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | `squadron` | PostgreSQL password |
| `KEYCLOAK_URL` | `http://keycloak:8080` | Keycloak base URL |
| `KEYCLOAK_CLIENT_SECRET` | `change-me-in-production` | Keycloak client secret |
| `SQUADRON_JWKS_URI` | `http://squadron-identity:8081/api/auth/jwks` | JWKS endpoint for JWT validation |
| `SQUADRON_ENCRYPTION_KEY` | `dev-encryption-key-change-in-production` | Symmetric key for encrypting secrets at rest |

#### AI Provider Variables

| Variable | Default | Description |
|---|---|---|
| `OPENAI_API_KEY` | `sk-placeholder` | OpenAI (or compatible) API key |
| `OPENAI_BASE_URL` | `https://api.openai.com` | OpenAI-compatible API endpoint |
| `OPENAI_MODEL` | `gpt-4o` | Default cloud AI model |
| `SPRING_AI_OLLAMA_BASE_URL` | `http://ollama:11434` | Ollama server URL |
| `SPRING_AI_OLLAMA_CHAT_MODEL` | `qwen2.5-coder:7b` | Default local AI model |

#### Service-Specific Variables

| Variable | Service | Description |
|---|---|---|
| `MAIL_HOST` | notification | SMTP server hostname (default: `mailpit`) |
| `MAIL_PORT` | notification | SMTP port (default: `1025`) |
| `SQUADRON_WORKSPACE_PROVIDER` | workspace | `docker` or `kubernetes` |
| `SQUADRON_WORKSPACE_DOCKER_HOST` | workspace | Docker socket path |
| `AUTH_MODE` | gateway | Authentication mode (default: `squadron`) |
| `REDIS_HOST` / `REDIS_PORT` | gateway | Redis connection for sessions/caching |

### Using a `.env` File

Create a `.env` file in `deploy/docker/` to override defaults without modifying
`docker-compose.yml`:

```bash
cp deploy/docker/.env.example deploy/docker/.env
```

Edit `deploy/docker/.env`:

```bash
# AI Provider
OPENAI_API_KEY=sk-proj-your-real-key
OPENAI_MODEL=gpt-4o

# Database credentials (change for production)
POSTGRES_USER=squadron
POSTGRES_PASSWORD=a-strong-password-here

# Keycloak admin
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=a-strong-password-here
```

Docker Compose automatically reads `.env` from the same directory as the compose file.

### Customizing Ports

Override host port bindings by setting environment variables or editing the `ports`
section in `docker-compose.yml`. Common customizations:

```yaml
# Example: move the UI to port 80 and gateway to 443
squadron-ui:
  ports:
    - "80:80"

squadron-gateway:
  ports:
    - "443:8443"
```

If port 8080 conflicts with another service, change the Keycloak host port:

```yaml
keycloak:
  ports:
    - "9080:8080"  # Access Keycloak admin at localhost:9080
```

### AI Provider Setup

Squadron supports two AI backends. You can use either or both simultaneously.

#### Option A: OpenAI (or Compatible API)

Set your API key and optionally customize the model:

```bash
export OPENAI_API_KEY="sk-proj-..."
export OPENAI_MODEL="gpt-4o"             # or gpt-4o-mini, gpt-4-turbo, etc.

# For Azure OpenAI or other compatible APIs:
export OPENAI_BASE_URL="https://your-instance.openai.azure.com"
```

#### Option B: Ollama (Local Models)

Ollama starts automatically with the Docker Compose stack. To pull a model:

```bash
# The compose file includes an ollama-pull sidecar that pulls qwen2.5-coder:7b
# automatically. To pull a different model:
curl http://localhost:11434/api/pull -d '{"name": "codellama:13b"}'

# Or use the ollama CLI inside the container:
docker compose exec ollama ollama pull deepseek-coder-v2:16b
```

To change the default Ollama model, set the `OLLAMA_MODEL` environment variable before
starting:

```bash
export OLLAMA_MODEL="deepseek-coder-v2:16b"
docker compose --profile services up -d
```

**GPU acceleration**: If you have an NVIDIA GPU with the
[NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/)
installed, Ollama will use it automatically. Verify with:

```bash
docker compose exec ollama ollama ps  # Should show GPU layers
```

### SMTP Configuration

The default SMTP target is Mailpit (a development mail catcher). For production, point
the notification service to a real SMTP server:

```yaml
# In docker-compose.yml, under squadron-notification environment:
MAIL_HOST: smtp.example.com
MAIL_PORT: 587
SPRING_MAIL_USERNAME: notifications@example.com
SPRING_MAIL_PASSWORD: your-smtp-password
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE: "true"
```

---

## Authentication Setup

### Keycloak Realm Creation

Keycloak starts in dev mode with default admin credentials (`admin` / `admin`). On
first deployment, you must create a realm and client for Squadron.

1. Open the Keycloak admin console at http://localhost:8080
2. Log in with `admin` / `admin`
3. Create a new realm named `squadron`
4. Under **Clients**, create a client:
   - Client ID: `squadron-app`
   - Client authentication: ON
   - Valid redirect URIs: `http://localhost:4200/*`, `http://localhost:8443/*`
   - Web origins: `http://localhost:4200`
5. Copy the client secret and set it in your environment:
   ```bash
   export KEYCLOAK_CLIENT_SECRET="your-client-secret"
   ```

### LDAP Integration

Squadron supports LDAP-backed user authentication through Keycloak's User Federation.

1. In the Keycloak admin console, navigate to **Realm settings > User federation**
2. Add an **LDAP** provider with your directory settings:
   - Connection URL: `ldap://your-ldap-server:389`
   - Bind DN: `cn=service-account,dc=example,dc=com`
   - Bind credential: your service account password
   - User DN: `ou=people,dc=example,dc=com`
   - Username LDAP attribute: `uid` (or `sAMAccountName` for Active Directory)

### Test LDAP Overlay

For development and testing, Squadron includes a test LDAP server with pre-populated
Futurama character accounts. Start it with the LDAP overlay:

```bash
docker compose -f docker-compose.yml -f docker-compose-testldap.yml \
  --profile services --profile frontend up -d
```

Or use the build script which includes it automatically:

```bash
./testldap-build-and-start.sh
```

**Test LDAP details**:

| Setting | Value |
|---|---|
| Image | `ghcr.io/rroemhild/docker-test-openldap:master` |
| LDAP port | 10389 |
| LDAPS port | 10636 |
| Base DN | `dc=planetexpress,dc=com` |
| Admin DN | `cn=admin,dc=planetexpress,dc=com` |
| Admin password | `GoodNewsEveryone` |
| User base | `ou=people,dc=planetexpress,dc=com` |

**Test accounts** (username / password):

| User | Password | Role |
|---|---|---|
| professor | professor | Admin |
| fry | fry | User |
| leela | leela | User |
| bender | bender | User |
| zoidberg | zoidberg | User |
| hermes | hermes | User |
| amy | amy | User |

Verify LDAP connectivity:

```bash
ldapsearch -H ldap://localhost:10389 -x \
  -b "ou=people,dc=planetexpress,dc=com" \
  -D "cn=admin,dc=planetexpress,dc=com" \
  -w GoodNewsEveryone "(objectClass=inetOrgPerson)" uid cn
```

---

## Connecting External Platforms

Squadron integrates with ticketing systems and Git platforms through the
`squadron-platform` and `squadron-git` services. Configure connections through the
API or UI after initial setup.

### JIRA

1. In JIRA, create an API token: **Account Settings > Security > API tokens**
2. In Squadron, add a platform connection:
   ```bash
   curl -X POST http://localhost:8443/api/platforms/connections \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "type": "JIRA",
       "baseUrl": "https://your-org.atlassian.net",
       "credentials": {
         "email": "user@example.com",
         "apiToken": "your-jira-api-token"
       }
     }'
   ```

### GitHub

1. Create a GitHub App or personal access token with repo, issues, and pull request
   permissions
2. Add the connection:
   ```bash
   curl -X POST http://localhost:8443/api/platforms/connections \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "type": "GITHUB",
       "baseUrl": "https://api.github.com",
       "credentials": {
         "token": "ghp_your-github-token"
       }
     }'
   ```

### GitLab

```bash
curl -X POST http://localhost:8443/api/platforms/connections \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "GITLAB",
    "baseUrl": "https://gitlab.example.com",
    "credentials": {
      "token": "glpat-your-gitlab-token"
    }
  }'
```

### Azure DevOps

```bash
curl -X POST http://localhost:8443/api/platforms/connections \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "AZURE_DEVOPS",
    "baseUrl": "https://dev.azure.com/your-org",
    "credentials": {
      "token": "your-azure-devops-pat"
    }
  }'
```

**Note**: Platform credentials are encrypted at rest using `SQUADRON_ENCRYPTION_KEY`.
Changing this key after connections are stored will make existing credentials
unreadable.

---

## Production Hardening

The default Docker Compose configuration is designed for development. Before exposing
Squadron to users or the internet, apply these changes.

### 1. Change All Default Passwords

**This is mandatory.** The defaults are public and insecure.

```bash
# In deploy/docker/.env:
POSTGRES_USER=squadron
POSTGRES_PASSWORD=$(openssl rand -base64 24)

KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=$(openssl rand -base64 24)
```

Update the `x-common-env` section in `docker-compose.yml`:

```yaml
x-common-env: &common-env
  SPRING_DATASOURCE_USERNAME: squadron
  SPRING_DATASOURCE_PASSWORD: "<your-strong-db-password>"
  KEYCLOAK_CLIENT_SECRET: "<your-keycloak-client-secret>"
```

### 2. Set a Real Encryption Key

The `SQUADRON_ENCRYPTION_KEY` is used to encrypt platform credentials, tokens, and
secrets stored in the database. The default `dev-encryption-key-change-in-production`
must be replaced.

```bash
# Generate a 256-bit key
openssl rand -base64 32
# Example output: K7xZ9p2mQfR3nL8vY5tW1cA6bD4eH0jG2iF7kM9oN3s=
```

Set it in your environment or `.env` file. **Back up this key securely.** If lost, all
encrypted data (platform tokens, OAuth credentials) becomes unrecoverable.

### 3. TLS Termination with a Reverse Proxy

Never expose Squadron services directly over HTTP in production. Place a reverse proxy
in front of the gateway.

#### Nginx Example

```nginx
# /etc/nginx/sites-available/squadron
server {
    listen 443 ssl http2;
    server_name squadron.example.com;

    ssl_certificate     /etc/letsencrypt/live/squadron.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/squadron.example.com/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;

    # API Gateway
    location /api/ {
        proxy_pass http://127.0.0.1:8443;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket (STOMP) for real-time updates
    location /ws/ {
        proxy_pass http://127.0.0.1:8443;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }

    # Frontend (Angular)
    location / {
        proxy_pass http://127.0.0.1:4200;
        proxy_set_header Host $host;
    }
}

server {
    listen 80;
    server_name squadron.example.com;
    return 301 https://$host$request_uri;
}
```

#### Caddy Example

```
squadron.example.com {
    handle /api/* {
        reverse_proxy localhost:8443
    }

    handle /ws/* {
        reverse_proxy localhost:8443
    }

    handle {
        reverse_proxy localhost:4200
    }
}
```

Caddy automatically provisions and renews Let's Encrypt certificates.

Update Keycloak to recognize the proxy:

```yaml
keycloak:
  environment:
    KC_HOSTNAME: squadron.example.com
    KC_PROXY_HEADERS: xforwarded
```

### 4. Backup Strategy

Set up automated daily backups for all persistent data.

**Database backups** (the most critical data):

```bash
#!/bin/bash
# /opt/squadron/backup-databases.sh
# Schedule: 0 2 * * * /opt/squadron/backup-databases.sh
set -euo pipefail

BACKUP_DIR="/var/backups/squadron/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"

DATABASES=(
  keycloak squadron_identity squadron_config squadron_orchestrator
  squadron_platform squadron_agent squadron_workspace
  squadron_git squadron_review squadron_notification
)

for db in "${DATABASES[@]}"; do
  docker compose -f /path/to/deploy/docker/docker-compose.yml \
    exec -T postgres pg_dump -U squadron --format=custom --compress=9 \
    "$db" > "${BACKUP_DIR}/${db}.dump"
done

# Prune backups older than 30 days
find /var/backups/squadron/ -maxdepth 1 -type d -mtime +30 -exec rm -rf {} +
```

**Redis snapshot**:

```bash
docker compose exec redis redis-cli BGSAVE
docker compose cp redis:/data/dump.rdb /var/backups/squadron/redis-dump.rdb
```

**Keycloak export** (realm configuration, not covered by database backup alone):

```bash
docker compose exec keycloak /opt/keycloak/bin/kc.sh export \
  --dir /tmp/export --realm squadron
docker compose cp keycloak:/tmp/export /var/backups/squadron/keycloak-export/
```

### 5. Resource Tuning

The default memory limits (512 MB per service, 256 MB reservation) work for light use.
For production loads, adjust based on observed usage:

```yaml
# In docker-compose.yml — example adjustments for heavy workloads
squadron-agent:
  deploy:
    resources:
      limits:
        memory: 1G       # Agent service uses more memory for AI conversations
      reservations:
        memory: 512M

squadron-orchestrator:
  deploy:
    resources:
      limits:
        memory: 768M     # Workflow engine with many concurrent tasks
      reservations:
        memory: 384M
```

Monitor actual usage with `docker stats` and adjust accordingly.

### 6. Restrict Published Ports

In production, only the reverse proxy should be accessible externally. Bind internal
services to localhost:

```yaml
postgres:
  ports:
    - "127.0.0.1:5432:5432"

redis:
  ports:
    - "127.0.0.1:6379:6379"

keycloak:
  ports:
    - "127.0.0.1:8080:8080"

nats:
  ports:
    - "127.0.0.1:4222:4222"
    - "127.0.0.1:8222:8222"
```

---

## Maintenance

### Starting and Stopping

```bash
cd deploy/docker

# Start everything
docker compose --profile services --profile frontend up -d

# Stop everything (preserves data volumes)
docker compose --profile services --profile frontend down

# Stop and destroy all data (DESTRUCTIVE)
docker compose --profile services --profile frontend down -v

# Restart a single service
docker compose restart squadron-orchestrator

# Using the build script
./testldap-build-and-start.sh --stop
./testldap-build-and-start.sh --skip-build   # Restart without rebuilding
```

### Viewing Logs

```bash
# All service logs
docker compose --profile services logs -f

# Single service logs
docker compose logs -f squadron-agent

# Last 200 lines
docker compose logs --tail=200 squadron-orchestrator

# Errors only (last 24 hours)
docker compose --profile services logs --since 24h 2>&1 | grep -i "error\|exception"

# Using the build script
./testldap-build-and-start.sh --logs
./testldap-build-and-start.sh --logs squadron-agent
```

### Health Checks

All backend services expose Spring Boot Actuator endpoints:

```bash
# Check a single service
curl -s http://localhost:8083/actuator/health | jq .

# Check all services
for port in 8443 8081 8082 8083 8084 8085 8086 8087 8088 8089; do
  status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${port}/actuator/health")
  echo "Port ${port}: HTTP ${status}"
done

# Check infrastructure
docker compose exec postgres pg_isready -U squadron
docker compose exec redis redis-cli ping
curl -s http://localhost:8222/healthz
curl -s http://localhost:8080/health/ready | jq .status
```

The gateway service also aggregates health from all downstream services at
`http://localhost:8443/actuator/health`.

### Updating Squadron

```bash
# 1. Pull latest source
git pull origin main

# 2. Rebuild
mvn clean package -q -DskipTests
cd squadron-ui && npm ci --silent && npx ng build --configuration=production && cd ..

# 3. Rebuild Docker images
for module in gateway identity config orchestrator platform agent workspace git review notification; do
  docker build -t "squadron/squadron-${module}:latest" -f "squadron-${module}/Dockerfile" .
done
docker build -t "squadron/squadron-ui:latest" -f squadron-ui/Dockerfile squadron-ui/

# 4. Rolling restart (recreate containers with new images)
cd deploy/docker
docker compose --profile services --profile frontend up -d --force-recreate

# Or use the build script
./testldap-build-and-start.sh
```

**Note**: Flyway database migrations run automatically on service startup. Always back
up databases before updating.

### Database Backups

See the backup script in [Production Hardening](#4-backup-strategy). For a quick manual
backup:

```bash
# Backup a single database
docker compose exec -T postgres pg_dump -U squadron --format=custom \
  squadron_orchestrator > squadron_orchestrator.dump

# Restore (WARNING: drops existing data)
docker compose exec -T postgres dropdb -U squadron squadron_orchestrator
docker compose exec -T postgres createdb -U squadron squadron_orchestrator
docker compose exec -T postgres pg_restore -U squadron -d squadron_orchestrator < squadron_orchestrator.dump
```

---

## Troubleshooting

### Out of Memory (Exit Code 137)

**Symptom**: `docker compose ps` shows one or more services with status `Exited (137)`.

**Cause**: The container exceeded its memory limit and was killed by the OOM killer.

**Fix**:
1. Check which container was killed: `docker compose ps | grep 137`
2. Increase memory limits in `docker-compose.yml`:
   ```yaml
   deploy:
     resources:
       limits:
         memory: 768M   # Increase from 512M
   ```
3. If the host itself is low on memory, reduce the number of running services or add
   more RAM. As a quick fix, start only infrastructure and the services you need:
   ```bash
   docker compose up -d postgres redis nats keycloak
   docker compose up -d squadron-gateway squadron-identity squadron-orchestrator
   ```
4. On Docker Desktop, increase the VM memory allocation under
   **Settings > Resources > Memory**.

### Port Conflicts

**Symptom**: `docker compose up` fails with `Bind for 0.0.0.0:8080 failed: port is already allocated`.

**Fix**: Identify the conflicting process and either stop it or change the Squadron
port:

```bash
# Find what's using the port
sudo lsof -i :8080
# or
sudo ss -tlnp | grep 8080

# Option 1: Stop the conflicting process
sudo systemctl stop apache2   # example

# Option 2: Change the Squadron port in docker-compose.yml
keycloak:
  ports:
    - "9080:8080"   # Move Keycloak to port 9080
```

Common conflicts: Keycloak (8080) with Apache/Tomcat, PostgreSQL (5432) with a local
install, Redis (6379) with a local install.

### Keycloak Startup Delays

**Symptom**: Backend services start but authentication fails. Keycloak health check
reports `starting` for 1-2 minutes.

**Cause**: Keycloak is slow to start, especially on first run (database table creation)
or with limited CPU.

**Fix**: This is normal behavior. The Compose health check has a 90-second start
period and 20 retries. If it consistently times out:

1. Check Keycloak logs: `docker compose logs keycloak`
2. Increase the start period:
   ```yaml
   keycloak:
     healthcheck:
       start_period: 120s  # Increase from 90s
   ```
3. Ensure PostgreSQL is healthy before Keycloak starts (it should be via `depends_on`)
4. Verify Keycloak's database connection:
   ```bash
   docker compose exec postgres psql -U squadron -d keycloak -c "SELECT 1;"
   ```

### Flyway Migration Errors

**Symptom**: A service fails to start with `FlywayException: Validate failed` or
`Migration checksum mismatch`.

**Cause**: An applied migration file was modified, or migrations are out of order.

**Fix**:

For development (data loss acceptable):
```bash
# Nuclear option: wipe the database and start fresh
docker compose --profile services --profile frontend down -v
docker compose --profile services --profile frontend up -d
```

For production (preserve data):
```bash
# 1. Check migration status
docker compose exec -T postgres psql -U squadron -d squadron_orchestrator \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;"

# 2. If a migration failed, fix the SQL and repair
docker compose exec -T postgres psql -U squadron -d squadron_orchestrator \
  -c "DELETE FROM flyway_schema_history WHERE success = false;"

# 3. Restart the service
docker compose restart squadron-orchestrator
```

### NATS Connection Issues

**Symptom**: Services log `NATS connection lost` or events are not being delivered.

**Fix**:

```bash
# Check NATS health
curl -s http://localhost:8222/healthz

# Check connected clients
curl -s http://localhost:8222/connz | jq '.num_connections'

# Check JetStream state
curl -s http://localhost:8222/jsz | jq '{streams, consumers, messages, bytes}'

# If JetStream data is corrupted, reset (DESTRUCTIVE — loses queued messages)
docker compose stop nats
docker volume rm docker_nats-data
docker compose up -d nats
```

### Services Cannot Resolve Each Other

**Symptom**: Services fail with `UnknownHostException: squadron-identity` or similar.

**Cause**: Container is not on the `squadron-net` network, or the target container is
not running.

**Fix**:

```bash
# Verify the network exists
docker network ls | grep squadron

# Check which containers are on the network
docker network inspect squadron-net --format '{{range .Containers}}{{.Name}} {{end}}'

# Restart with network recreation
docker compose --profile services --profile frontend down
docker compose --profile services --profile frontend up -d
```

### Ollama Model Not Available

**Symptom**: Agent service returns errors about missing models.

**Fix**:

```bash
# Check which models are available
docker compose exec ollama ollama list

# Pull the default model
docker compose exec ollama ollama pull qwen2.5-coder:7b

# Or trigger via the API
curl http://localhost:11434/api/pull -d '{"name": "qwen2.5-coder:7b"}'

# Check if ollama-pull sidecar ran successfully
docker compose logs ollama-pull
```

### Docker Socket Permission Denied (Workspace Service)

**Symptom**: `squadron-workspace` cannot create sandbox containers; logs show
`Permission denied: /var/run/docker.sock`.

**Fix**: The workspace service needs access to the Docker socket. Ensure the mount
exists in `docker-compose.yml`:

```yaml
squadron-workspace:
  volumes:
    - /var/run/docker.sock:/var/run/docker.sock
```

On systems with restrictive socket permissions, add the container user to the docker
group or adjust permissions:

```bash
# Check socket permissions
ls -la /var/run/docker.sock

# If needed, make the socket group-readable (less secure)
sudo chmod 666 /var/run/docker.sock
```

---

## Port Reference

| Service | Container Port | Host Port | Purpose |
|---|---|---|---|
| PostgreSQL | 5432 | 5432 | Primary database |
| PgBouncer | 5432 | 6432 | Connection pooler |
| Redis | 6379 | 6379 | Cache, sessions |
| NATS | 4222 | 4222 | Message broker (client) |
| NATS | 8222 | 8222 | Message broker (monitoring) |
| Keycloak | 8080 | 8080 | Identity provider |
| Mailpit | 1025 | 1025 | SMTP (dev) |
| Mailpit | 8025 | 8025 | Web UI (dev) |
| Ollama | 11434 | 11434 | Local AI models |
| squadron-gateway | 8443 | 8443 | API Gateway |
| squadron-identity | 8081 | 18081 | Identity service |
| squadron-config | 8082 | 8082 | Configuration service |
| squadron-orchestrator | 8083 | 8083 | Workflow engine |
| squadron-platform | 8084 | 8084 | Ticketing platform adapters |
| squadron-agent | 8085 | 8085 | AI Agent service |
| squadron-workspace | 8086 | 8086 | Sandbox management |
| squadron-git | 8087 | 8087 | Git operations |
| squadron-review | 8088 | 8088 | Code review orchestration |
| squadron-notification | 8089 | 8089 | Notifications |
| squadron-ui | 80 | 4200 | Angular frontend |

## Volume Reference

| Volume | Container Path | Purpose |
|---|---|---|
| `postgres-data` | `/var/lib/postgresql/data` | Database storage |
| `redis-data` | `/data` | Redis AOF persistence |
| `nats-data` | `/data` | JetStream message storage |
| `keycloak-data` | `/opt/keycloak/data` | Keycloak local storage |
| `ollama-data` | `/root/.ollama` | Downloaded AI models |
