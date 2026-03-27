# Squadron Architecture

## Overview

Squadron is an AI-powered software development workflow platform that integrates with
ticketing systems (JIRA, GitHub Issues, GitLab Issues, Azure DevOps) and Git platforms
(GitHub, GitLab, Bitbucket) to automate planning, coding, code review, and QA using
configurable AI agent squadrons.

Developers command a configurable "squadron" of AI agents that operate in sandboxed
container environments, transforming tickets into production-ready, reviewed, and tested
code.

## Core Workflow

```
BACKLOG -> PRIORITIZED -> PLANNING -> PROPOSE_CODE -> REVIEW -> QA -> MERGE -> DONE
```

| Stage | What Happens |
|---|---|
| **Backlog** | Tickets imported from ticketing platform |
| **Prioritized** | Team/lead prioritizes work |
| **Planning** | Workspace spins up, planning agent proposes implementation plan |
| **Propose Code** | Coding agent implements the approved plan |
| **Review** | AI reviewer + human reviewers evaluate code |
| **QA** | QA agent analyzes coverage, generates missing tests |
| **Merge** | Code merged via configured strategy (merge/squash/rebase) |
| **Done** | Ticket status synced back to ticketing platform |

## Service Topology

```
                              +-------------------+
                              |    Angular 21     |
                              |    Frontend       |
                              |    (Port 80)      |
                              +--------+----------+
                                       |
                                       | HTTPS
                                       v
                              +-------------------+
                              |   API Gateway     |
                              |  (Port 8443)      |
                              |  Spring Cloud GW  |
                              +--------+----------+
                                       |
               +-----------+-----------+-----------+-----------+
               |           |           |           |           |
               v           v           v           v           v
         +-----------+ +-----------+ +-----------+ +-----------+ +-----------+
         | Identity  | | Orchestr. | |   Agent   | | Workspace | |    Git    |
         | :8081     | | :8083     | | :8085     | | :8086     | | :8087     |
         +-----------+ +-----------+ +-----------+ +-----------+ +-----------+
               |           |           |           |           |
               v           v           v           v           v
         +-----------+ +-----------+ +-----------+ +-----------+ +-----------+
         |  Config   | | Platform  | |  Review   | | Notific.  | |           |
         | :8082     | | :8084     | | :8088     | | :8089     | |           |
         +-----------+ +-----------+ +-----------+ +-----------+ +-----------+

         Infrastructure Layer:
         +----------+  +---------+  +-------+  +----------+  +---------+
         |PostgreSQL|  |  Redis  |  | NATS  |  | Keycloak |  |  Vault  |
         | :5432    |  | :6379   |  | :4222 |  | :8080    |  | :8200   |
         +----------+  +---------+  +-------+  +----------+  +---------+
```

## Services

### API Gateway (`squadron-gateway` - Port 8443)

Single entry point for all client requests. Routes requests to appropriate
microservices, handles rate limiting (via Redis), JWT validation, TLS termination,
and WebSocket upgrade for real-time agent chat.

### Identity Service (`squadron-identity` - Port 8081)

Manages multi-tenancy, organizations, teams, and user profiles. Delegates
authentication to Keycloak. Provides RBAC with roles: ADMIN, TEAM_LEAD, DEVELOPER,
QA, VIEWER. Supports multiple auth providers: Keycloak, LDAP, generic OIDC.

### Config Service (`squadron-config` - Port 8082)

Centralized hierarchical configuration: `default -> tenant -> team -> user` (most
specific wins). Manages workflow definitions, squadron configs, review policies, Git
strategies. Notifies services of configuration changes via NATS.

### Task Orchestrator (`squadron-orchestrator` - Port 8083)

Core workflow engine with a custom PostgreSQL-backed state machine. Manages task
lifecycle through the 8-state pipeline. Validates transitions against configurable
guards, emits events to NATS on every state change, and triggers downstream actions
(workspace creation, agent invocation).

### Platform Integration (`squadron-platform` - Port 8084)

Adapter layer for external ticketing platforms. Provides bidirectional sync using the
user's own OAuth tokens (delegated identity). Supports JIRA Cloud/Server, GitHub
Issues, GitLab Issues, and Azure DevOps via the adapter pattern.

### Agent Service (`squadron-agent` - Port 8085)

The brain of Squadron. Manages AI agent lifecycle, conversation management, and
multi-provider support via Spring AI. Four agent types: Planning, Coding, Review, QA.
Features tool-calling (file read/write, shell exec, search) in sandboxed workspaces.
Tracks token usage and cost attribution per tenant/team/user.

### Workspace Manager (`squadron-workspace` - Port 8086)

Creates and manages sandboxed container environments where agents operate. Supports
Kubernetes pods (preferred) and Docker containers (fallback). Handles workspace
lifecycle, resource quotas, and workspace pooling for faster startup.

### Git Service (`squadron-git` - Port 8087)

Manages all Git operations and platform API interactions. Supports PR/MR creation on
GitHub, GitLab, and Bitbucket. Configurable branch strategies (GitFlow, trunk-based,
custom). Handles merge operations with conflict detection.

### Review Service (`squadron-review` - Port 8088)

Orchestrates code review combining AI and human reviewers. Enforces review policies
(minimum approvals, AI review required). Manages QA reports with coverage analysis.
Gates transitions on policy satisfaction.

### Notification Service (`squadron-notification` - Port 8089)

Event-driven notifications via NATS. Multi-channel delivery: in-app (WebSocket),
email, Slack webhooks, Teams webhooks. Per-user notification preferences with muted
event types. Retry mechanism with dead letter queue.

## Data Flow

```
  Ticketing Platform                                          Git Platform
  (JIRA, GitHub, etc.)                                       (GitHub, GitLab, etc.)
         |                                                          ^
         | 1. Import tickets                             8. Merge PR |
         v                                                          |
  +-------------+    2. State    +-------------+    7. Create  +----------+
  |  Platform   | ------------> | Orchestrator | -----------> |   Git    |
  |  Service    |   transitions |   (Engine)   |      PR      | Service  |
  +-------------+               +------+-------+              +----------+
                                       |                          ^
                           3. Trigger  |                          |
                              agent    |                  6. Code |
                                       v                  changes |
                                +-------------+           +----------+
                                |    Agent    | --------> |Workspace |
                                |   Service   |  4. Tool  | Manager  |
                                +------+------+   calls   +----------+
                                       |
                               5. Review|
                                       v
                                +-------------+
                                |   Review    |
                                |   Service   |
                                +-------------+
```

### Detailed Flow

1. **Import**: Platform Service imports tickets from the external ticketing system
2. **State Machine**: Orchestrator manages task progression through workflow states
3. **Agent Invocation**: On state entry (e.g., PLANNING), Orchestrator publishes NATS
   event; Agent Service subscribes and starts the appropriate agent
4. **Tool Execution**: Agents use tools (file read/write, shell, search) executed inside
   sandboxed workspaces via the Workspace Manager
5. **Review**: Agent produces review comments or QA reports, sent to Review Service
6. **Code Changes**: Coding agent commits changes to the workspace
7. **PR Creation**: Git Service creates a pull request on the Git platform
8. **Merge**: After all gates pass, Git Service merges the PR

## Inter-Service Communication

### Synchronous (REST via Feign)

Used for request/response operations where the caller needs an immediate result.

| Caller | Target | Purpose |
|---|---|---|
| Agent -> Workspace | Execute commands in sandbox |
| Agent -> Git | Create branches, PRs, check mergeability |
| Agent -> Review | Submit reviews, check gates |
| Orchestrator -> Platform | Sync task status |
| Gateway -> All services | Route client requests |

### Asynchronous (NATS JetStream)

Used for event-driven workflows, long-running operations, and notifications.

| Stream | Publisher | Subscribers |
|---|---|---|
| `squadron.tasks.state-changed` | Orchestrator | Agent, Notification, Platform |
| `squadron.agents.invoked` | Orchestrator | Agent |
| `squadron.agents.completed` | Agent | Orchestrator, Notification |
| `squadron.workspaces.lifecycle` | Workspace | Agent, Notification |
| `squadron.reviews.updated` | Review | Orchestrator, Notification |
| `squadron.git.events` | Git | Notification, Orchestrator |
| `squadron.notifications` | Various | Notification |

### Real-Time (WebSocket)

STOMP over WebSocket via the API Gateway for:
- Real-time agent chat streaming
- Live task status updates
- In-app notification push

Redis Pub/Sub serves as the WebSocket message broker for multi-instance fan-out.

## Database Architecture

### Database-per-Service Pattern

Each service owns its database (or schema in a shared PostgreSQL instance).

| Service | Database |
|---|---|
| Identity | `squadron_identity` |
| Config | `squadron_config` |
| Orchestrator | `squadron_orchestrator` |
| Platform | `squadron_platform` |
| Agent | `squadron_agent` |
| Workspace | `squadron_workspace` |
| Git | `squadron_git` |
| Review | `squadron_review` |
| Notification | `squadron_notification` |

### Connection Pooling

```
  Service (HikariCP)  -->  PgBouncer (transaction pooling)  -->  PostgreSQL 17
```

- **HikariCP**: In-JVM connection pool (Spring Boot default)
- **PgBouncer**: External connection pooler in transaction mode, multiplexes many
  application connections into fewer PostgreSQL connections

### Multi-Tenancy

Row-level tenant isolation. Every table includes `tenant_id UUID NOT NULL`. All queries
are filtered by tenant via Hibernate filters or PostgreSQL Row Level Security (RLS).

### Schema Migrations

Flyway manages all schema migrations. Migration files are located at
`src/main/resources/db/migration/` within each service module, following the naming
convention `V{version}__{description}.sql`.

## Security Architecture

### Authentication Flow

```
  Browser  --OIDC (Auth Code + PKCE)-->  Keycloak
  Browser  --Bearer JWT-->               API Gateway
  API Gateway  --Validate JWT-->         Keycloak JWKS
  API Gateway  --Forward + Headers-->    Microservices
```

- **Keycloak 26.x** as the identity provider
- **OAuth 2.0 Authorization Code + PKCE** for the Angular frontend
- **JWT access tokens** validated at the Gateway and propagated downstream
- **RBAC roles**: `squadron-admin`, `team-lead`, `developer`, `qa`, `viewer`

### Certificate-Based Security (mTLS)

- **HashiCorp Vault** as the root CA (PKI Secrets Engine)
- **cert-manager** on Kubernetes for automated certificate management
- Short-lived certificates (24-72h TTL)
- All inter-service communication over mTLS
- External traffic over TLS 1.3

### Delegated User Identity

When Squadron acts on external platforms (JIRA, GitHub, etc.), it uses the **user's
own OAuth tokens**, not a service account. This ensures:

- Audit trails on external platforms reflect the actual user
- Users control their own platform access
- Revocation is per-user

### Secrets Management

HashiCorp Vault stores all secrets: database credentials, API keys, platform tokens,
encryption keys. Spring Cloud Vault provides automatic injection into Spring Boot.

## Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Backend | Spring Boot | 3.5.3 |
| AI/LLM | Spring AI | 1.0.0 |
| Frontend | Angular | 21.x |
| Database | PostgreSQL + PgBouncer | 17.x |
| Cache | Redis | 7.x |
| Message Broker | NATS with JetStream | 2.x |
| Identity | Keycloak | 26.x |
| Secrets | HashiCorp Vault | latest |
| Monitoring | Prometheus + Grafana | latest |
| Container Runtime | Kubernetes / Docker | 1.31+ / 27+ |
| Build Tool | Maven | 3.9.x |
| Java | OpenJDK | 21 LTS |

## Deployment Models

### Kubernetes (Production)

- Three namespaces: `squadron-system`, `squadron-workspaces`, `squadron-infra`
- Each service: Deployment (2+ replicas), Service, HPA, PDB, NetworkPolicy
- Helm umbrella chart with per-service sub-charts
- NGINX Ingress or Traefik for TLS termination
- Prometheus Operator + Grafana for monitoring

### Docker Compose (Development)

- Single `docker-compose.yml` with all services and infrastructure
- Profile-based: `--profile services` for backend, `--profile frontend` for UI
- Docker socket mounted for workspace creation

### Air-Gapped

- All images in an internal registry (Harbor or any OCI-compatible)
- Helm chart values for internal registry references
- Self-hosted AI models via Cohere Command-4 on-prem or Ollama
- Full offline installation bundle
