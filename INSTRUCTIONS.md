# INSTRUCTIONS.md - Development Instructions for Squadron

## Overview

Squadron is an AI-powered development workflow platform. This document provides
instructions for developing, building, and running the application.

## Prerequisites

- **Java 21 LTS** (OpenJDK or equivalent)
- **Maven 3.9.x**
- **Node.js 22.x** and **npm 10.x** (for the Angular frontend)
- **Docker** and **Docker Compose** (for infrastructure and local development)
- **kubectl** (optional, for Kubernetes deployments)
- **Helm 3.x** (optional, for Kubernetes deployments)

## Getting Started

### 1. Clone the Repository

```bash
git clone <repo-url> squadron
cd squadron
```

### 2. Start Infrastructure

```bash
cd deploy/docker
docker-compose up -d
```

This starts:
- PostgreSQL 17 (port 5432)
- Redis 7 (port 6379)
- NATS with JetStream (port 4222, monitoring on 8222)
- Keycloak 26 (port 8080)
- HashiCorp Vault (port 8200)
- Prometheus (port 9090)
- Grafana (port 3000)

### 3. Build the Backend

```bash
cd /path/to/squadron
mvn clean install
```

### 4. Run Services

Each service can be run independently:

```bash
# From the respective module directory
cd squadron-gateway && mvn spring-boot:run
cd squadron-identity && mvn spring-boot:run
cd squadron-orchestrator && mvn spring-boot:run
# ... etc.
```

Or use Docker Compose to run everything:

```bash
cd deploy/docker
docker-compose --profile all up -d
```

### 5. Build and Run the Frontend

```bash
cd squadron-ui
npm install
npm start
```

The frontend will be available at `https://localhost:4200`.

## Project Modules

| Module | Port | Description |
|---|---|---|
| `squadron-gateway` | 8443 | API Gateway (Spring Cloud Gateway) |
| `squadron-identity` | 8081 | Tenant/User management + Keycloak integration |
| `squadron-config` | 8082 | Centralized configuration service |
| `squadron-orchestrator` | 8083 | Task workflow engine |
| `squadron-platform` | 8084 | Ticketing platform adapters |
| `squadron-agent` | 8085 | AI Agent Service (Spring AI) |
| `squadron-workspace` | 8086 | Sandboxed container management |
| `squadron-git` | 8087 | Git operations + platform APIs |
| `squadron-review` | 8088 | Code review orchestration |
| `squadron-notification` | 8089 | Notification service |
| `squadron-ui` | 4200 | Angular frontend (dev server) |

## Configuration

### Environment-Specific Configuration

Each service uses Spring profiles for environment-specific configuration:

- `application.yml` -- default/shared configuration
- `application-dev.yml` -- local development overrides
- `application-docker.yml` -- Docker Compose deployment
- `application-k8s.yml` -- Kubernetes deployment

### Key Environment Variables

| Variable | Description | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/squadron` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `squadron` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | (from Vault) |
| `SPRING_DATA_REDIS_HOST` | Redis host | `localhost` |
| `NATS_URL` | NATS server URL | `nats://localhost:4222` |
| `KEYCLOAK_URL` | Keycloak base URL | `http://localhost:8080` |
| `VAULT_URI` | Vault base URL | `http://localhost:8200` |
| `VAULT_TOKEN` | Vault access token | (dev token for local) |

### Squadron Configuration Hierarchy

Configuration is resolved in order of specificity (most specific wins):

1. **Default** -- system-wide defaults
2. **Tenant** -- organization-level overrides
3. **Team** -- team-level overrides
4. **User** -- individual user overrides

## Multi-Tenancy

Squadron supports both single-tenant and multi-tenant modes, configurable via:

```yaml
squadron:
  tenancy:
    mode: MULTI_TENANT  # or SINGLE_TENANT
```

In multi-tenant mode:
- All tables include a `tenant_id` column
- Row-Level Security (RLS) enforces tenant isolation
- Each tenant can have its own Keycloak realm (configurable)

## Ticketing Platform Integration

Supported platforms:

| Platform | Auth Methods |
|---|---|
| JIRA Cloud | OAuth 2.0 (3-legged) |
| JIRA Server / Data Centre | Personal Access Token, Basic Auth |
| GitHub Issues | GitHub App, OAuth |
| GitLab Issues | OAuth2 |
| Azure DevOps | OAuth2, Personal Access Token |

All platform operations use **delegated user identity** -- the user's own credentials
are used so that actions appear as the user on the external platform.

## Git Platform Integration

Supported platforms for PR/MR operations:

| Platform | Auth Methods |
|---|---|
| GitHub | GitHub App, OAuth, PAT |
| GitLab | OAuth2, PAT |
| Bitbucket | OAuth2, App Password |

## AI Agent Configuration

Each agent type (planning, coding, review, QA) can be configured independently with:
- Provider (Claude via GitHub, Cohere self-hosted, OpenAI, Ollama, etc.)
- Model selection
- Temperature, max tokens
- System prompt overrides
- Tool permissions
- Token budgets

## Security

- All external traffic over **TLS 1.3**
- All inter-service communication over **mTLS**
- **Keycloak** for authentication (OAuth 2.0 + OIDC)
- **HashiCorp Vault** for secrets management and PKI
- **cert-manager** for automated certificate lifecycle (Kubernetes)
- JWT tokens validated at the API Gateway and propagated to services

## Monitoring

- **Spring Boot Actuator** endpoints on each service (`/actuator/health`, `/actuator/prometheus`)
- **Prometheus** scrapes metrics from all services
- **Grafana** dashboards for visualization
- **Loki** for log aggregation (optional)

## Air-Gapped Deployment

For deployments without internet access:

1. Pre-pull all container images and save as tarballs
2. Load images into an internal registry (Harbor, etc.)
3. Update Helm chart values to point to the internal registry
4. Deploy AI models on-prem (Cohere Command-4 self-hosted, Ollama)
5. Deploy Keycloak, Vault, PostgreSQL, Redis, NATS on-prem

## Testing

### Test Requirements

Every class must have corresponding tests. The testing strategy follows a pyramid:

1. **Unit Tests** (JUnit 5 + Mockito)
   - All services, controllers, engines, adapters, and utility classes
   - Use `@WebMvcTest` for controller unit tests
   - Mock all dependencies with `@MockBean` or `@Mock`
   - Target: 100% line coverage for business logic

2. **Integration Tests** (Testcontainers)
   - Repository/JPA tests with `@DataJpaTest` and Testcontainers PostgreSQL
   - Service integration tests with `@SpringBootTest` and real database
   - NATS event publishing/consuming tests
   - Redis integration tests

3. **E2E Tests**
   - Full workflow tests: task lifecycle through all states
   - Agent conversation flow (mock LLM responses)
   - Platform sync round-trip (mock external APIs with WireMock)

### Running Tests

```bash
# Run all tests
mvn clean verify

# Run tests for a specific module
mvn test -pl squadron-common

# Run only unit tests (exclude integration)
mvn test -pl squadron-orchestrator -Dgroups="unit"

# Run only integration tests
mvn test -pl squadron-orchestrator -Dgroups="integration"

# Run with coverage report
mvn verify -pl squadron-orchestrator jacoco:report
```

### Test Naming Convention

Use descriptive names: `should_<expectedBehavior>_when_<condition>()`

Examples:
- `should_createTenant_when_validRequest()`
- `should_throwNotFound_when_tenantMissing()`
- `should_transitionToPlanning_when_taskIsPrioritized()`
- `should_rejectTransition_when_invalidStateChange()`

## Pull Requests

After implementing a feature or completing a module:

1. Ensure all tests pass: `mvn clean verify`
2. Create a feature branch if not already on one
3. Commit with descriptive message
4. Create a pull request on the appropriate Git platform (GitHub, GitLab, or Bitbucket)
5. Include a summary of changes, testing performed, and any deployment notes

## Useful Commands

```bash
# Build everything
mvn clean install

# Build without tests (faster)
mvn clean install -DskipTests

# Run a specific service
cd squadron-orchestrator && mvn spring-boot:run

# Run tests for a specific module
mvn test -pl squadron-common

# Run all tests and verify
mvn clean verify

# Check for dependency updates
mvn versions:display-dependency-updates

# Generate OpenAPI docs (when services are running)
curl http://localhost:8083/v3/api-docs | jq .

# Format code (if formatter is configured)
mvn spotless:apply
```
