# Squadron

**AI-Powered Software Development Workflow Platform**

Squadron integrates with ticketing systems (JIRA, GitHub Issues, GitLab Issues, Azure DevOps) and Git platforms (GitHub, GitLab, Bitbucket) to automate planning, coding, code review, and QA using configurable AI agent squadrons.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Microservices](#microservices)
- [Frontend](#frontend)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Quick Start with Docker](#quick-start-with-docker)
  - [Quick Start with Test LDAP](#quick-start-with-test-ldap)
  - [Running Individual Services](#running-individual-services)
- [Configuration](#configuration)
  - [Environment Variables](#environment-variables)
  - [Hierarchical Configuration](#hierarchical-configuration)
- [Infrastructure](#infrastructure)
- [Inter-Service Communication](#inter-service-communication)
- [Security](#security)
- [Deployment](#deployment)
  - [Docker Compose (Development)](#docker-compose-development)
  - [Kubernetes with Helm](#kubernetes-with-helm)
  - [Air-Gapped Deployment](#air-gapped-deployment)
- [Testing](#testing)
- [Monitoring and Observability](#monitoring-and-observability)
- [Load Testing](#load-testing)
- [Port Reference](#port-reference)
- [License](#license)

---

## Overview

Squadron automates the software development lifecycle through an AI-driven workflow pipeline:

```
BACKLOG -> PRIORITIZED -> PLANNING -> PROPOSE_CODE -> REVIEW -> QA -> MERGE -> DONE
```

Each stage is handled by a specialized AI agent:

| Stage | Agent | Responsibility |
|-------|-------|----------------|
| **PLANNING** | Planning Agent | Analyzes codebase + ticket, produces implementation plan |
| **PROPOSE_CODE** | Coding Agent | Generates code changes in a sandboxed workspace |
| **REVIEW** | Review Agent | Automated code review with policy enforcement |
| **QA** | QA Agent | Generates tests targeting 100% line coverage |
| **MERGE** | Merge Agent | Creates and manages pull/merge requests |

Tasks flow through the pipeline automatically. Human reviewers can intervene at any gate. The system supports multiple LLM providers (OpenAI, Ollama) and can operate fully air-gapped with local models.

---

## Architecture

```
                        +-------------------+
                        |   Angular 19 UI   |
                        | (Dashboard, Chat, |
                        |  Admin Console)   |
                        +---------+---------+
                                  |
                        +---------+---------+
                        |   API Gateway     |
                        | (Spring Cloud GW) |
                        | JWT + Rate Limit  |
                        +---------+---------+
                                  |
          +-------+-------+-------+-------+-------+-------+
          |       |       |       |       |       |       |
       +--+--+ +--+--+ +--+--+ +--+--+ +--+--+ +--+--+ +--+--+
       |Orch.| |Agent| |Work | |Plat | | Git | | Rev | |Notif|
       |     | |     | |space| |form | |     | | iew | |     |
       +--+--+ +--+--+ +--+--+ +--+--+ +--+--+ +--+--+ +--+--+
          |       |       |       |       |       |       |
     +----+----+--+--+----+----+--+--+----+----+--+--+----+
     |         |      |         |      |         |        |
  +--+--+  +--+--+ +--+--+  +--+--+ +--+--+  +--+--+ +--+--+
  |Postgr| |Redis| | NATS | |Keycl| |Vault| |Ollama| |Mail |
  | SQL  | |     | |  JS  | |oak  | |     | |      | |pit  |
  +------+ +-----+ +------+ +-----+ +-----+ +------+ +-----+
```

**Key architectural patterns:**

- **Custom PostgreSQL state machine** for workflow orchestration (not Temporal, not Spring Statemachine)
- **Adapter pattern** for ticketing platforms and Git platforms
- **Hierarchical configuration** (default -> tenant -> team -> user; most specific wins)
- **Row-level multi-tenancy** with `tenant_id` on all tables
- **Delegated user identity** -- uses the user's own OAuth tokens for external API calls
- **NATS JetStream** for async inter-service events with durable delivery
- **Spring Cloud OpenFeign** for sync inter-service REST calls
- **STOMP over WebSocket** for real-time frontend updates

See [ARCHITECTURE.md](ARCHITECTURE.md) for the complete architecture document.

---

## Technology Stack

### Backend

| Component | Technology | Version |
|-----------|-----------|---------|
| Framework | Spring Boot | 3.5.3 |
| Cloud | Spring Cloud | 2025.0.0 |
| AI/LLM | Spring AI | 1.0.0 |
| Language | Java | 21 LTS |
| Database | PostgreSQL | 17 |
| Connection Pool | PgBouncer + HikariCP | |
| Cache | Redis | 7.x |
| Message Broker | NATS with JetStream | |
| Identity | Keycloak | 26.x |
| Secrets | HashiCorp Vault | |
| Migrations | Flyway | 11.8.2 |
| API Docs | SpringDoc OpenAPI | 2.8.6 |
| Build | Maven | 3.9.x |

### Frontend

| Component | Technology | Version |
|-----------|-----------|---------|
| Framework | Angular | 19 |
| Language | TypeScript | 5.6 |
| WebSocket | @stomp/stompjs | 7.3 |
| Reactive | RxJS | 7.8 |

### Infrastructure

| Component | Technology |
|-----------|-----------|
| Container Runtime | Docker / Kubernetes |
| Orchestration | Helm charts |
| Certificates | Vault PKI + cert-manager |
| Monitoring | Prometheus + Grafana |
| Email (dev) | Mailpit |
| Local LLM | Ollama |

---

## Project Structure

Maven multi-module monorepo:

```
squadron/
  pom.xml                        # Parent POM with dependency management
  ARCHITECTURE.md                # Full architecture document
  TODO.md                        # Implementation progress tracker
  testldap-build-and-start.sh    # Full build + start script with test LDAP
  squadron-common/               # Shared DTOs, security, utilities, NATS config
  squadron-gateway/              # API Gateway (Spring Cloud Gateway)
  squadron-identity/             # Tenant/user management + Keycloak + LDAP
  squadron-config/               # Hierarchical configuration service
  squadron-orchestrator/         # Task workflow engine (PostgreSQL state machine)
  squadron-platform/             # Ticketing platform adapters
  squadron-agent/                # AI Agent Service (Spring AI)
  squadron-workspace/            # Sandboxed container management (K8s/Docker)
  squadron-git/                  # Git operations + platform APIs
  squadron-review/               # Code review orchestration
  squadron-notification/         # Event-driven notifications
  squadron-ui/                   # Angular 19 frontend
  deploy/
    docker/                      # Docker Compose files + init scripts
    helm/squadron/               # Helm umbrella chart
    k8s/                         # Raw Kubernetes manifests
    vault/                       # Vault PKI + mTLS setup
    security/                    # Trivy scanning + OWASP dependency-check
    loadtest/                    # k6 load testing scenarios
    airgap/                      # Air-gapped deployment scripts
    terraform/                   # Cloud infrastructure provisioning (placeholder)
```

### Package Naming Convention

All Java packages use: `com.squadron.<module>.<subpackage>`

Examples:
- `com.squadron.common.dto`
- `com.squadron.orchestrator.engine`
- `com.squadron.agent.service`
- `com.squadron.platform.adapter.jira`

---

## Microservices

### squadron-common (shared library)

Shared library included by all services. Contains:
- DTOs (TaskDto, TenantDto, TeamDto, UserDto, ProjectDto, etc.)
- Events (TaskStateChanged, AgentInvoked, AgentCompleted, ReviewUpdated, etc.)
- Security (TenantContext, TenantFilter, JwtService, TokenEncryption, AccessLevel)
- NATS config (NatsConfig, NatsEventPublisher with JetStream, JetStreamSubscriber)
- OpenFeign config (FeignConfig, FeignErrorDecoder)
- Resilience (CircuitBreaker, RetryHelper, ResilientClient)
- Audit system (AuditService, AuditAspect, AuditController)
- Health indicators (NatsHealthIndicator)

### squadron-gateway (API Gateway)

Spring Cloud Gateway handling all external traffic:
- JWT validation and propagation
- Rate limiting (Redis-backed)
- CORS configuration
- WebSocket upgrade support (`/ws/**` routes)
- Request logging and tenant header injection
- Downstream service health aggregation (reactive `ServiceHealthIndicator`)

### squadron-identity (Identity & Access)

Multi-tenant identity management:
- Tenant, team, and user CRUD
- Auth providers: Keycloak, LDAP, OIDC
- Security groups and RBAC permissions (ADMIN, TEAM_LEAD, DEVELOPER, QA, VIEWER)
- RSA key management for JWT signing
- JWKS endpoint (`/api/auth/jwks`)
- AuthProviderConfig management

### squadron-orchestrator (Workflow Engine)

Custom PostgreSQL-backed state machine:
- Task lifecycle management (BACKLOG through DONE)
- State transitions with validation
- Workflow configuration and initialization
- NATS event publishing on state changes
- Integration with Platform Service for ticket sync

### squadron-agent (AI Agent Service)

AI-powered code generation and review:
- Multi-provider LLM support (OpenAI-compatible, Ollama)
- 4 agent types: Planning, Coding, Review, QA
- Tool calling system (ToolRegistry, ToolExecutionEngine)
- Conversation management with context
- Squadron configuration management
- Token usage tracking
- WebSocket controller for real-time agent chat
- Coverage analysis service

### squadron-platform (Ticketing Integration)

Adapter pattern for ticketing system integration:
- JIRA Cloud adapter
- JIRA Server adapter
- GitHub Issues adapter
- GitLab Issues adapter
- Azure DevOps adapter
- OAuth2 token management (delegated user identity)
- Webhook processing for real-time sync
- Platform sync service

### squadron-git (Git Operations)

Git platform integration and operations:
- Platform adapters: GitHub, GitLab, Bitbucket
- Git operations service (clone, branch, commit, push)
- Branch strategy management
- PR/MR creation and management
- Diff service for code analysis

### squadron-workspace (Sandbox Management)

Sandboxed development environments:
- Kubernetes pod provider
- Docker container provider
- Workspace lifecycle management (create, start, stop, destroy)
- Resource quota enforcement
- Workspace cleanup scheduler
- Git integration within workspaces

### squadron-review (Code Review)

AI and human code review orchestration:
- Review policy engine
- Review gate service (approval/rejection)
- QA report management
- Configurable review rules per tenant/team

### squadron-config (Configuration)

Hierarchical configuration management:
- 4-level hierarchy: default -> tenant -> team -> user
- Most specific configuration wins
- JSON-based flexible configuration
- REST API for configuration CRUD

### squadron-notification (Notifications)

Multi-channel notification delivery:
- Email (via Mailpit in dev, SMTP in production)
- Slack integration
- Microsoft Teams integration
- In-app notifications
- NATS event-driven (4 durable subscriptions)
- Notification preferences per user
- Retry service for failed deliveries

---

## Frontend

Angular 19 single-page application served by nginx. Key features:

- **Dashboard** -- Task board (Kanban view), project overview
- **Agent Chat** -- Real-time AI agent interaction via STOMP WebSocket
- **Code Review** -- Diff viewer for reviewing proposed changes
- **QA Reports** -- Test coverage and quality metrics
- **Admin Console** -- User, team, security group, and permission management
- **Task Management** -- Create, edit, and track tasks through the pipeline

The nginx reverse proxy forwards `/api/` requests to the API Gateway with WebSocket support. The frontend uses OIDC authentication via Keycloak.

### Build

```bash
cd squadron-ui
npm ci
npx ng build --configuration=production
```

The production build outputs to `dist/squadron-ui/browser`, which is served by nginx at port 80 (mapped to 4200 on the host).

---

## Getting Started

### Prerequisites

- **Java 21** (JDK, not just JRE)
- **Maven 3.9+**
- **Node.js 22+** with npm
- **Docker** with Docker Compose v2
- At least **8 GB of available RAM** for Docker (all services + infrastructure)

### Quick Start with Docker

Build and start everything:

```bash
# Copy environment file and customize
cp deploy/docker/.env.example deploy/docker/.env

# Build all Maven modules, Angular frontend, Docker images, and start
./testldap-build-and-start.sh
```

The script will:
1. Check prerequisites (java, maven, node, docker)
2. Build all Maven modules (`mvn clean package`)
3. Build the Angular frontend (`ng build --configuration=production`)
4. Build Docker images for all 10 backend services + the UI
5. Start infrastructure (PostgreSQL, Redis, NATS, Keycloak, PgBouncer, Mailpit, Ollama)
6. Wait for all infrastructure to become healthy
7. Start all backend services and the frontend
8. Wait for all services to become healthy
9. Display access URLs

### Quick Start with Test LDAP

The `testldap-build-and-start.sh` script includes a pre-populated OpenLDAP server with Futurama character accounts for testing:

```bash
./testldap-build-and-start.sh
```

**Test LDAP details:**

| Property | Value |
|----------|-------|
| Image | `ghcr.io/rroemhild/docker-test-openldap:master` |
| Domain | `dc=planetexpress,dc=com` |
| Admin DN | `cn=admin,dc=planetexpress,dc=com` |
| Admin Password | `GoodNewsEveryone` |
| User Base DN | `ou=people,dc=planetexpress,dc=com` |
| LDAP Port | 10389 |
| LDAPS Port | 10636 |

**Test users** (password = username):

| User | UID |
|------|-----|
| Professor Farnsworth | `professor` |
| Philip J. Fry | `fry` |
| Turanga Leela | `leela` |
| Bender Rodriguez | `bender` |
| Dr. Zoidberg | `zoidberg` |
| Hermes Conrad | `hermes` |
| Amy Wong | `amy` |

Verify LDAP is working:

```bash
ldapsearch -H ldap://localhost:10389 -x \
  -b "ou=people,dc=planetexpress,dc=com" \
  -D "cn=admin,dc=planetexpress,dc=com" \
  -w GoodNewsEveryone \
  "(objectClass=inetOrgPerson)"
```

### Script Options

```bash
./testldap-build-and-start.sh                # Full build and start
./testldap-build-and-start.sh --infra        # Infrastructure only (for local dev)
./testldap-build-and-start.sh --skip-build   # Start without rebuilding
./testldap-build-and-start.sh --skip-tests   # Build without running tests
./testldap-build-and-start.sh --clean        # Remove all data and start fresh
./testldap-build-and-start.sh --stop         # Stop all services
./testldap-build-and-start.sh --status       # Show service status
./testldap-build-and-start.sh --logs         # Tail all logs
./testldap-build-and-start.sh --logs <svc>   # Tail logs for a specific service
./testldap-build-and-start.sh --pull-model   # Pull the default Ollama model
./testldap-build-and-start.sh --no-gpu       # Start Ollama without GPU
```

### Running Individual Services

For local development, start infrastructure then run services individually:

```bash
# Start infrastructure only
./testldap-build-and-start.sh --infra

# Run a specific service from its module directory
cd squadron-identity
mvn spring-boot:run

# Or run with a specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=docker
```

---

## Configuration

### Environment Variables

Core environment variables (set in `deploy/docker/.env` or shell):

| Variable | Default | Description |
|----------|---------|-------------|
| `OPENAI_API_KEY` | `sk-placeholder` | OpenAI API key |
| `OPENAI_BASE_URL` | `https://api.openai.com` | OpenAI-compatible API base URL |
| `OPENAI_MODEL` | `gpt-4o` | Default OpenAI model |
| `OLLAMA_MODEL` | `qwen2.5-coder:7b` | Default Ollama model |
| `POSTGRES_USER` | `squadron` | PostgreSQL username |
| `POSTGRES_PASSWORD` | `squadron` | PostgreSQL password |
| `KEYCLOAK_ADMIN` | `admin` | Keycloak admin username |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin` | Keycloak admin password |

Additional variables injected by Docker Compose:

| Variable | Value | Description |
|----------|-------|-------------|
| `SPRING_PROFILES_ACTIVE` | `docker` | Spring profile for Docker environment |
| `NATS_URL` | `nats://nats:4222` | NATS server URL |
| `KEYCLOAK_URL` | `http://keycloak:8080` | Keycloak base URL |
| `SQUADRON_JWKS_URI` | `http://squadron-identity:8081/api/auth/jwks` | JWKS endpoint for JWT validation |
| `SQUADRON_ENCRYPTION_KEY` | (dev key) | AES encryption key for token storage |

### Hierarchical Configuration

Squadron supports 4-level hierarchical configuration:

```
default (system-wide) -> tenant -> team -> user
```

The most specific configuration wins. Configuration is managed through the Config Service REST API and stored as JSONB in PostgreSQL. This allows tenants and teams to customize agent behavior, review policies, notification preferences, and more without code changes.

---

## Infrastructure

All infrastructure runs as Docker containers with health checks:

| Service | Image | Purpose | Health Check |
|---------|-------|---------|-------------|
| PostgreSQL 17 | `postgres:17-alpine` | Primary database (database-per-service) | `pg_isready` |
| PgBouncer | `edoburu/pgbouncer:latest` | Connection pooling (transaction mode) | Running state |
| Redis 7 | `redis:7-alpine` | Caching, rate limiting, pub/sub | `redis-cli ping` |
| NATS | `nats:2-alpine` | JetStream message broker (10 streams) | HTTP healthz |
| Keycloak 26 | `quay.io/keycloak/keycloak:26.0.5` | OAuth 2.0 / OIDC identity provider | HTTP health/ready |
| Ollama | `ollama/ollama:latest` | Local LLM inference | `ollama list` |
| Mailpit | `axllent/mailpit:latest` | Dev email capture (SMTP + web UI) | Running state |

### Database-per-Service

PostgreSQL hosts separate databases for each service, created automatically by `deploy/docker/init-databases.sql`:

- `squadron` (shared/default)
- `squadron_identity`
- `squadron_config`
- `squadron_orchestrator`
- `squadron_platform`
- `squadron_agent`
- `squadron_workspace`
- `squadron_git`
- `squadron_review`
- `squadron_notification`
- `keycloak`

All databases have the `pgcrypto` extension installed. Schema migrations are managed by Flyway (16 migration files across all services).

### NATS JetStream Streams

10 durable streams for reliable event delivery:

| Stream | Subjects | Purpose |
|--------|----------|---------|
| TASKS | `squadron.tasks.*` | Task state changes |
| AGENTS | `squadron.agents.*` | Agent invocations and completions |
| WORKSPACES | `squadron.workspaces.*` | Workspace lifecycle events |
| REVIEWS | `squadron.reviews.*` | Review updates |
| GIT_EVENTS | `squadron.git.*` | Git operations and PR events |
| NOTIFICATIONS | `squadron.notifications.*` | Notification delivery |
| CONFIG | `squadron.config.*` | Configuration changes |
| PLATFORM | `squadron.platform.*` | Platform sync events |
| AUDIT | `squadron.audit.*` | Audit trail events |
| COVERAGE | `squadron.coverage.*` | Code coverage reports |

---

## Inter-Service Communication

### Synchronous (OpenFeign)

Request-response calls between services, with circuit breaker and retry wrappers:

| Caller | Target | Feign Client |
|--------|--------|-------------|
| squadron-agent | squadron-orchestrator | `OrchestratorClient` |
| squadron-agent | squadron-git | `GitServiceClient` |
| squadron-agent | squadron-review | `ReviewServiceClient` |
| squadron-agent | squadron-workspace | `WorkspaceServiceClient` |
| squadron-orchestrator | squadron-platform | `PlatformServiceClient` |

All Feign clients have resilient wrappers with circuit breaker + retry patterns.

### Asynchronous (NATS JetStream)

Event-driven communication with durable subscriptions and ack/nak handling:

- 7 NATS listeners across services (Planning, Coding, Review, QA, Merge, PlanApproval, Notifications)
- JetStream-first publish with core NATS fallback
- Standardized subject naming: `squadron.<domain>.<event>`

### Real-Time (WebSocket)

STOMP over WebSocket for live frontend updates:
- Agent chat messages
- Task state change notifications
- Review comments
- Redis pub/sub for multi-instance fan-out

---

## Security

### Authentication

- **Keycloak 26** as the identity provider (OAuth 2.0 + PKCE for the Angular SPA)
- **LDAP integration** for enterprise directory authentication
- **OIDC** support for external identity providers
- JWT tokens validated at the API Gateway, propagated to downstream services
- JWKS endpoint served by squadron-identity (`/api/auth/jwks`)

### Authorization

Role-based access control with 5 roles:

| Role | Description |
|------|-------------|
| `ADMIN` | Full system access |
| `TEAM_LEAD` | Team management + all developer permissions |
| `DEVELOPER` | Task and code operations |
| `QA` | Review and QA operations |
| `VIEWER` | Read-only access |

### Encryption

- AES-256-GCM encryption for OAuth tokens stored in the database
- Vault Transit engine integration for key management
- All sensitive configuration (API keys, passwords) stored encrypted

### mTLS (Production)

- Vault PKI Secrets Engine for CA management (root + intermediate)
- cert-manager for automatic certificate rotation in Kubernetes
- All inter-service traffic encrypted with mutual TLS
- Setup scripts in `deploy/vault/`

---

## Deployment

### Docker Compose (Development)

```bash
# Start everything (infrastructure + services + frontend)
docker compose -f deploy/docker/docker-compose.yml \
  --profile services --profile frontend up -d

# Infrastructure only
docker compose -f deploy/docker/docker-compose.yml up -d

# With test LDAP
docker compose -f deploy/docker/docker-compose.yml \
  -f deploy/docker/docker-compose-testldap.yml \
  --profile services --profile frontend up -d

# Stop
docker compose -f deploy/docker/docker-compose.yml \
  --profile services --profile frontend down
```

Or use the convenience script:

```bash
./testldap-build-and-start.sh           # Build + start everything
./testldap-build-and-start.sh --stop    # Stop everything
```

### Kubernetes with Helm

The Helm umbrella chart deploys all services and infrastructure:

```bash
# Install
helm install squadron deploy/helm/squadron/ \
  -n squadron-system --create-namespace \
  -f deploy/helm/squadron/values.yaml

# Production
helm install squadron deploy/helm/squadron/ \
  -n squadron-system --create-namespace \
  -f deploy/helm/squadron/values.yaml \
  -f deploy/helm/squadron/values-prod.yaml

# Upgrade
helm upgrade squadron deploy/helm/squadron/ \
  -n squadron-system
```

**Helm chart features:**
- 3 Kubernetes namespaces: `squadron-system`, `squadron-workspaces`, `squadron-infra`
- Default 2 replicas per service with HPA
- Resource defaults: 250m-500m CPU, 512Mi-1Gi memory per service
- Network policies for service isolation
- Prometheus ServiceMonitor (30s scrape interval)
- PostgreSQL StatefulSet with 50Gi persistent volume
- NATS with JetStream (10Gi storage)
- Redis (5Gi storage)

Raw Kubernetes manifests are also available in `deploy/k8s/` for environments without Helm.

### Air-Gapped Deployment

For environments without internet access:

```bash
# On a connected machine: bundle all images
./deploy/airgap/bundle-images.sh

# Transfer squadron-images.tar.gz to the air-gapped environment

# On the air-gapped machine: load and push to internal registry
./deploy/airgap/load-images.sh --registry registry.internal:5000

# Deploy with air-gap Helm values
helm install squadron deploy/helm/squadron/ \
  -f deploy/airgap/values-airgap.yaml
```

Use Ollama with a locally pre-loaded model for AI capabilities without external API access.

---

## Testing

### Test Strategy

| Level | Framework | Scope |
|-------|-----------|-------|
| **Unit** | JUnit 5 + Mockito | All services, controllers, engines, adapters, utilities |
| **Integration** | Testcontainers | Repository layers, service interactions (PostgreSQL, Redis, NATS) |
| **E2E** | Full Spring context | Critical workflows (task lifecycle, agent conversation, platform sync) |
| **Frontend** | Karma + Jasmine | 47 component specs |

### Running Tests

```bash
# All unit tests
mvn clean test

# All tests including integration
mvn clean verify

# Single module
mvn clean test -pl squadron-identity

# With coverage report
mvn clean verify -pl squadron-agent
```

### Test Conventions

- Every class has corresponding unit tests
- Test classes mirror the main source structure in `src/test/java/`
- Descriptive test names: `should_createTenant_when_validRequest()`, `should_throwNotFound_when_tenantMissing()`
- `@SpringBootTest` for integration tests
- `@WebMvcTest` for controller unit tests
- `@DataJpaTest` with Testcontainers for repository tests

### Test Statistics

| Module | Source Files | Test Files |
|--------|:-----------:|:----------:|
| squadron-common | 60 | 65 |
| squadron-gateway | 8 | 8 |
| squadron-identity | 42 | 42 |
| squadron-orchestrator | 33 | 33 |
| squadron-agent | 78 | 76 |
| squadron-workspace | 16 | 16 |
| squadron-platform | 33 | 35 |
| squadron-git | 34 | 36 |
| squadron-review | 26 | 27 |
| squadron-config | 11 | 11 |
| squadron-notification | 24 | 24 |
| **Total** | **365** | **377** |
| squadron-ui | 30 components | 47 specs |

---

## Monitoring and Observability

### Health Checks

All backend services expose Spring Boot Actuator endpoints:

- `GET /actuator/health` -- service health status (includes NATS connectivity)
- `GET /actuator/info` -- application info

In production (with the `prod` profile), Kubernetes probes are also enabled:

- `GET /actuator/health/liveness` -- liveness probe
- `GET /actuator/health/readiness` -- readiness probe

The API Gateway aggregates health from all downstream services via its `ServiceHealthIndicator`.

### Prometheus Metrics

All services expose Prometheus metrics at `GET /actuator/prometheus`:

- JVM metrics (memory, GC, threads)
- HTTP request metrics (count, duration, status codes)
- Database connection pool metrics
- NATS connection metrics
- Custom business metrics

A Prometheus ServiceMonitor is included in the Helm chart (30s scrape interval).

### Logging

All services use structured logging via SLF4J + Logback. Logs include:
- Request correlation IDs
- Tenant context
- Service name and version

View logs in Docker:

```bash
./testldap-build-and-start.sh --logs                   # All services
./testldap-build-and-start.sh --logs squadron-agent     # Specific service
```

---

## Load Testing

k6-based load testing with 4 scenarios:

| Scenario | Description |
|----------|-------------|
| `auth-flow.js` | Authentication and token flow |
| `task-workflow.js` | End-to-end task lifecycle |
| `agent-chat.js` | AI agent chat interactions |
| `review-qa.js` | Code review and QA pipeline |

```bash
# Run load tests
./deploy/loadtest/run-loadtest.sh
```

Tuning configurations for PostgreSQL, PgBouncer, and NATS are provided in `deploy/loadtest/tuning/`.

---

## Port Reference

### Application Services

| Service | Port | Description |
|---------|:----:|-------------|
| squadron-ui | 4200 | Angular frontend (nginx) |
| squadron-gateway | 8443 | API Gateway |
| squadron-identity | 8081 | Identity & Access |
| squadron-config | 8082 | Configuration |
| squadron-orchestrator | 8083 | Workflow Engine |
| squadron-platform | 8084 | Ticketing Integration |
| squadron-agent | 8085 | AI Agent Service |
| squadron-workspace | 8086 | Sandbox Management |
| squadron-git | 8087 | Git Operations |
| squadron-review | 8088 | Code Review |
| squadron-notification | 8089 | Notifications |

### Infrastructure

| Service | Port | Description |
|---------|:----:|-------------|
| PostgreSQL | 5432 | Primary database |
| PgBouncer | 6432 | Connection pooler |
| Redis | 6379 | Cache and pub/sub |
| NATS | 4222 | Message broker (client) |
| NATS Monitor | 8222 | NATS monitoring UI |
| Keycloak | 8080 | Identity provider admin |
| Mailpit SMTP | 1025 | Dev email (SMTP) |
| Mailpit UI | 8025 | Dev email (web UI) |
| Ollama | 11434 | Local LLM API |

### Test LDAP (when using testldap-build-and-start.sh)

| Service | Port | Description |
|---------|:----:|-------------|
| OpenLDAP | 10389 | LDAP protocol |
| OpenLDAP | 10636 | LDAPS (TLS) |

---

## Build Commands

```bash
mvn clean compile        # Compile all modules
mvn clean test           # Run unit tests
mvn clean verify         # Run all tests including integration
mvn clean package        # Build JARs
mvn clean install        # Install to local Maven repo
```

---

## License

Proprietary. All rights reserved.
