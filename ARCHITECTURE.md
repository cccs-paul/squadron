# Squadron - Architecture Plan

## 1. Overview

**Squadron** is an AI-powered software development workflow platform that integrates with
ticketing systems and Git platforms to automate and assist with planning, coding, code review,
and QA. Developers command a configurable "squadron" of AI agents that operate in sandboxed
container environments.

### 1.1 Core Workflow

```
BACKLOG -> PRIORITIZED -> PLANNING -> PROPOSE_CODE -> REVIEW -> QA -> MERGE -> DONE
```

| Stage | What Happens |
|---|---|
| **Backlog** | Tickets imported from ticketing platform (JIRA, GitHub Issues, GitLab Issues, Azure DevOps) |
| **Prioritized** | Team/lead prioritizes work |
| **Planning** | Workspace spins up, planning agent analyzes codebase + ticket, proposes implementation plan. Developer refines via chat. |
| **Propose Code** | Coding agent implements the approved plan, generates code changes in the workspace |
| **Review** | Configurable mix of AI reviewer + human reviewers. All must approve. |
| **QA** | QA agent analyzes test coverage, identifies gaps, generates missing tests. Human QA optional. Target: 100% line coverage. |
| **Merge** | Code merged into the appropriate branch per the team's Git flow strategy |
| **Done** | Ticket status synced back to the ticketing platform |

### 1.2 Technology Stack Summary

| Layer | Technology | Version |
|---|---|---|
| **Backend** | Spring Boot | 3.5.3 |
| **AI / LLM** | Spring AI | 1.0.0 |
| **Frontend** | Angular | 21.x |
| **Database** | PostgreSQL + PgBouncer | 17.x |
| **Cache** | Redis | 7.x |
| **Message Broker** | NATS with JetStream | 2.x |
| **Identity** | Keycloak | 26.x |
| **Monitoring** | Prometheus + Grafana | latest |
| **Container Runtime** | Kubernetes (preferred) / Docker (fallback) | 1.31+ / 27+ |
| **Certificates** | HashiCorp Vault + cert-manager | latest |
| **Java** | 21 LTS | 21 |
| **Build Tool** | Maven | 3.9.x |

---

## 2. High-Level Architecture

```
+-----------------------------------------------------------------------+
|                         SQUADRON PLATFORM                             |
|                                                                       |
|  +--------------+   +--------------+   +--------------------------+   |
|  |   Angular    |   |   Angular    |   |   Angular                |   |
|  |   Dashboard  |   |   Agent Chat |   |   Config / Admin Console |   |
|  +------+-------+   +------+-------+   +----------+---------------+   |
|         +------------------+------------------------+                 |
|                            | HTTPS                                    |
|                    +-------v-------+                                  |
|                    |  API Gateway  |  (Spring Cloud Gateway)          |
|                    +-------+-------+                                  |
|         +------------------+------------------------+                 |
|         |                  |                        |                 |
|  +------v------+  +-------v-------+  +-------------v----------+      |
|  |  Task       |  |  Agent        |  |  Workspace             |      |
|  |  Orchestr.  |  |  Service      |  |  Manager               |      |
|  |  Service    |  |               |  |                        |      |
|  +------+------+  +-------+-------+  +-------------+----------+      |
|         |                  |                        |                 |
|  +------v------+  +-------v-------+  +-------------v----------+      |
|  |  Platform   |  |  Review       |  |  Git                   |      |
|  |  Integration|  |  Service      |  |  Service               |      |
|  |  Service    |  |               |  |                        |      |
|  +-------------+  +---------------+  +------------------------+      |
|                                                                       |
|  +-------------+  +---------------+  +------------------------+      |
|  |  Config     |  |  Notification |  |  Tenant / User         |      |
|  |  Service    |  |  Service      |  |  Management Service    |      |
|  +-------------+  +---------------+  +------------------------+      |
|                                                                       |
|  Infrastructure: NATS | PostgreSQL | Redis | Keycloak | Vault        |
+-----------------------------------------------------------------------+
```

---

## 3. Microservice Architecture

### 3.1 API Gateway (`squadron-gateway`)

**Purpose:** Single entry point for all client requests. Handles routing, rate limiting,
and TLS termination.

- **Framework:** Spring Cloud Gateway
- **Responsibilities:**
  - Route requests to appropriate microservices
  - TLS termination (or passthrough to services for mTLS)
  - Rate limiting per user/tenant (via Redis)
  - JWT token validation (delegated to Keycloak)
  - WebSocket upgrade handling (for real-time agent chat)
  - Request/response logging for audit

### 3.2 Task Orchestrator Service (`squadron-orchestrator`)

**Purpose:** The core workflow engine. Manages the lifecycle of tasks through the
Squadron pipeline using a custom PostgreSQL-backed state machine.

- **Responsibilities:**
  - Custom state machine backed by PostgreSQL
  - State transition validation with configurable guards
  - Trigger actions on state transitions (e.g., entering `PLANNING` triggers workspace
    creation + agent invocation)
  - Bidirectional sync with external ticketing platforms (push status changes back via
    Platform Integration Service)
  - Workflow configuration per team/project (which stages are enabled, auto-advance
    rules, required approvals)
  - Emit events to NATS on every state transition for downstream services

- **State Machine Design (PostgreSQL-backed):**

  ```sql
  -- Workflow definition (configurable per tenant/team)
  CREATE TABLE workflow_definitions (
      id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      tenant_id       UUID NOT NULL REFERENCES tenants(id),
      team_id         UUID REFERENCES teams(id),  -- nullable for tenant-level default
      name            VARCHAR(255) NOT NULL,
      states          JSONB NOT NULL,              -- ordered list of states
      transitions     JSONB NOT NULL,              -- allowed from->to with guards
      hooks           JSONB NOT NULL,              -- actions triggered on enter/exit
      created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
      updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
  );

  -- Current state of each task in the workflow
  CREATE TABLE task_workflows (
      id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      tenant_id       UUID NOT NULL REFERENCES tenants(id),
      task_id         UUID NOT NULL REFERENCES tasks(id),
      current_state   VARCHAR(50) NOT NULL,
      previous_state  VARCHAR(50),
      transition_at   TIMESTAMP NOT NULL DEFAULT NOW(),
      transitioned_by UUID NOT NULL,               -- user or system
      metadata        JSONB
  );

  -- Full audit trail of all state transitions
  CREATE TABLE task_state_history (
      id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      task_workflow_id UUID NOT NULL REFERENCES task_workflows(id),
      from_state       VARCHAR(50),
      to_state         VARCHAR(50) NOT NULL,
      triggered_by     UUID NOT NULL,
      reason           TEXT,
      created_at       TIMESTAMP NOT NULL DEFAULT NOW()
  );
  ```

  The state machine logic lives in a `WorkflowEngine` component that:

  1. Loads the applicable `workflow_definition` (team-level override or tenant-level fallback)
  2. Validates the requested transition against allowed transitions + guards
  3. Executes pre-transition hooks (e.g., check all reviews approved before allowing `QA`)
  4. Updates `task_workflows.current_state` atomically with `SELECT ... FOR UPDATE`
  5. Inserts into `task_state_history`
  6. Publishes a `TaskStateChanged` event to NATS
  7. Executes post-transition hooks (e.g., create workspace, invoke agent)

### 3.3 Agent Service (`squadron-agent`)

**Purpose:** Manages AI agent lifecycle, orchestration, and conversation management.
This is the brain of Squadron.

- **Framework:** Spring AI 1.0.0
- **Responsibilities:**
  - Agent lifecycle management (create, configure, invoke, terminate)
  - Conversation management (chat history, context windows, multi-turn interactions)
  - Multi-provider support through Spring AI's model abstraction:
    - Claude via GitHub Copilot (OpenAI-compatible endpoint)
    - Cohere Command-4 (self-hosted, via REST adapter)
    - OpenAI models
    - Any OpenAI-compatible endpoint (Ollama for local, vLLM, etc.)
  - Four agent types:
    - **Planning agent:** ticket description + codebase context -> implementation plan
    - **Coding agent:** approved plan + codebase -> code changes (diffs/commits)
    - **Review agent:** code changes -> structured review comments
    - **QA agent:** code changes -> test coverage analysis -> gap report + test generation
  - Tool/function calling: agents can invoke tools (file read/write, shell commands,
    test runners) inside sandboxed workspaces via Spring AI's tool calling
  - Token usage tracking and cost attribution per tenant/team/user
  - Streaming responses via WebSocket/SSE for real-time chat UX

- **Agent Squadron Configuration Model:**

  ```sql
  CREATE TABLE squadron_configs (
      id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      tenant_id       UUID NOT NULL REFERENCES tenants(id),
      team_id         UUID REFERENCES teams(id),     -- nullable
      user_id         UUID REFERENCES users(id),     -- nullable; most specific wins
      name            VARCHAR(255) NOT NULL,
      config          JSONB NOT NULL,
      created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
      updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
  );
  ```

  Example `config` JSONB:

  ```json
  {
    "planning_agent": {
      "provider": "claude-github",
      "model": "claude-sonnet-4-20250514",
      "max_tokens": 8192,
      "temperature": 0.3,
      "system_prompt_override": null
    },
    "coding_agent": {
      "provider": "cohere-self-hosted",
      "model": "command-a-03-2025",
      "max_tokens": 16384,
      "temperature": 0.1
    },
    "review_agent": {
      "provider": "claude-github",
      "model": "claude-sonnet-4-20250514",
      "max_tokens": 4096
    },
    "qa_agent": {
      "provider": "claude-github",
      "model": "claude-sonnet-4-20250514",
      "max_tokens": 8192
    },
    "max_concurrent_agents": 5,
    "token_budget_daily": 1000000,
    "tool_permissions": ["file_read", "file_write", "shell_exec", "test_run"]
  }
  ```

### 3.4 Workspace Manager Service (`squadron-workspace`)

**Purpose:** Creates and manages sandboxed container environments where agents operate
on code.

- **Responsibilities:**
  - Create isolated workspaces (K8s pods preferred, Docker containers as fallback)
  - Clone/checkout code into workspace from Git
  - Mount appropriate credentials (Git tokens, registry creds) securely
  - Manage workspace lifecycle (create, monitor, cleanup)
  - Expose shell/exec interface for Agent Service to run commands inside workspaces
  - Resource quota enforcement (CPU, memory, disk per workspace)
  - Workspace pooling for faster startup (pre-warmed containers)

- **Container Abstraction Layer:**

  ```java
  public interface WorkspaceProvider {
      Workspace create(WorkspaceSpec spec);
      void destroy(String workspaceId);
      ExecResult exec(String workspaceId, String[] command);
      void copyFilesIn(String workspaceId, Path source, String destPath);
      void copyFilesOut(String workspaceId, String sourcePath, Path dest);
      WorkspaceStatus status(String workspaceId);
  }

  // Implementations:
  // - KubernetesWorkspaceProvider (io.kubernetes:client-java)
  // - DockerWorkspaceProvider (com.github.docker-java:docker-java)
  ```

  The active provider is selected via application configuration, making the sandbox
  strategy fully configurable.

- **Workspace Table:**

  ```sql
  CREATE TABLE workspaces (
      id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      tenant_id       UUID NOT NULL REFERENCES tenants(id),
      task_id         UUID NOT NULL REFERENCES tasks(id),
      user_id         UUID NOT NULL REFERENCES users(id),
      provider_type   VARCHAR(50) NOT NULL,  -- KUBERNETES | DOCKER
      container_id    VARCHAR(512),           -- K8s pod name or Docker container ID
      status          VARCHAR(50) NOT NULL,   -- CREATING | READY | ACTIVE | TERMINATING | TERMINATED
      repo_url        VARCHAR(1024) NOT NULL,
      branch          VARCHAR(255),
      base_image      VARCHAR(512),
      resource_limits JSONB,
      created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
      terminated_at   TIMESTAMP
  );
  ```

### 3.5 Platform Integration Service (`squadron-platform`)

**Purpose:** Adapter layer for all external ticketing and project management platforms.

- **Responsibilities:**
  - Bidirectional sync with ticketing platforms
  - Import tasks/tickets into Squadron
  - Push status changes, comments, and attachments back to platforms
  - Webhook receivers for real-time updates from platforms
  - OAuth2 token management for platform APIs (using delegated user identity)

- **Adapter Pattern:**

  ```java
  public interface TicketingPlatformAdapter {
      List<Task> fetchTasks(ProjectRef project, TaskFilter filter);
      Task getTask(String externalId);
      void updateTaskStatus(String externalId, String status, String comment);
      void addComment(String externalId, String comment);
      void addAttachment(String externalId, String filename, byte[] content);
      List<String> getAvailableStatuses(String projectId);
      WebhookRegistration registerWebhook(String projectId, String callbackUrl);
  }
  ```

  Implementations:

  | Adapter | Platform | Auth |
  |---|---|---|
  | `JiraCloudAdapter` | Atlassian REST API v3 | OAuth 2.0 (3LO) |
  | `JiraServerAdapter` | Atlassian REST API v2 | PAT / Basic Auth |
  | `GitHubIssuesAdapter` | GitHub REST / GraphQL API | GitHub App / OAuth |
  | `GitLabIssuesAdapter` | GitLab REST API v4 | OAuth2 |
  | `AzureDevOpsAdapter` | Azure DevOps REST API | OAuth2 / PAT |

- **Platform Connections:**

  ```sql
  CREATE TABLE platform_connections (
      id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      tenant_id       UUID NOT NULL REFERENCES tenants(id),
      platform_type   VARCHAR(50) NOT NULL,   -- JIRA_CLOUD | JIRA_SERVER | GITHUB | GITLAB | AZURE_DEVOPS
      base_url        VARCHAR(1024) NOT NULL,
      auth_type       VARCHAR(50) NOT NULL,    -- OAUTH2 | PAT | BASIC | API_KEY
      credentials     JSONB NOT NULL,          -- encrypted; Vault reference or encrypted blob
      status          VARCHAR(50) NOT NULL,    -- ACTIVE | INACTIVE | ERROR
      metadata        JSONB,                   -- project mappings, custom field mappings, etc.
      created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
      updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
  );
  ```

- **User-Delegated Identity:**

  Each user links their own platform credentials (OAuth tokens or PATs). When Squadron
  performs actions on a platform (updating ticket status, adding comments), it uses the
  **user's own credentials**, not a service account. This ensures the audit trail on the
  ticketing platform reflects the actual user who performed the action.

  ```sql
  CREATE TABLE user_platform_tokens (
      id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id         UUID NOT NULL REFERENCES users(id),
      connection_id   UUID NOT NULL REFERENCES platform_connections(id),
      access_token    VARCHAR(2048) NOT NULL,  -- encrypted, stored in Vault
      refresh_token   VARCHAR(2048),           -- encrypted, stored in Vault
      expires_at      TIMESTAMP,
      scopes          VARCHAR(1024),
      created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
      updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
  );
  ```

### 3.6 Git Service (`squadron-git`)

**Purpose:** Manages all Git operations and Git platform API interactions.

- **Responsibilities:**
  - Git operations via CLI (`ProcessBuilder`) executed inside workspace containers:
    clone, checkout, branch, commit, push, merge
  - Pull/Merge Request creation and management via platform APIs
  - Branch strategy configuration per team (GitFlow, trunk-based, custom)
  - Merge conflict detection and reporting
  - Diff generation for review

- **Git Platform Adapter:**

  ```java
  public interface GitPlatformAdapter {
      PullRequest createPullRequest(PullRequestSpec spec);
      void mergePullRequest(String prId, MergeStrategy strategy);
      void addReviewComment(String prId, ReviewComment comment);
      List<ReviewComment> getReviewComments(String prId);
      DiffResult getDiff(String prId);
      void requestReview(String prId, List<String> reviewers);
      BranchProtection getBranchProtection(String repoId, String branch);
  }
  ```

  Implementations:

  | Adapter | Platform |
  |---|---|
  | `GitHubPlatformAdapter` | GitHub REST / GraphQL API |
  | `GitLabPlatformAdapter` | GitLab REST API v4 |
  | `BitbucketPlatformAdapter` | Bitbucket REST API 2.0 |

### 3.7 Review Service (`squadron-review`)

**Purpose:** Orchestrates code review combining AI and human reviewers.

- **Responsibilities:**
  - Define review policies per team (minimum human approvals, AI review required/optional)
  - Invoke AI review agent via Agent Service
  - Track human review status (approved, changes requested, commented)
  - Aggregate review verdicts (all required approvals met?)
  - Present unified review dashboard (AI comments + human comments)
  - Gate transition to QA on review policy satisfaction

- **Review Configuration Example:**

  ```json
  {
    "min_human_approvals": 1,
    "require_ai_review": true,
    "auto_request_reviewers": ["team-leads"],
    "self_review_allowed": true,
    "ai_review_model": "review_agent",
    "review_checklist": ["security", "performance", "testing", "documentation"]
  }
  ```

### 3.8 Config Service (`squadron-config`)

**Purpose:** Centralized configuration management for tenants, teams, and users.

- **Responsibilities:**
  - Hierarchical configuration: `default` -> `tenant` -> `team` -> `user`
    (most specific wins)
  - Workflow definitions (which stages, transitions, guards)
  - Squadron configurations (agent assignments, model selection)
  - Platform connection settings
  - Git branching strategies
  - Review policies
  - QA policies (coverage thresholds, required test types)
  - Configuration versioning and audit trail
  - Configuration validation
  - Runtime configuration reload (notify services via NATS)

### 3.9 Notification Service (`squadron-notification`)

**Purpose:** Event-driven notifications to users across multiple channels.

- **Responsibilities:**
  - Consume events from NATS (state changes, review requests, agent completions)
  - Multi-channel delivery: WebSocket (in-app), email, Slack/Teams webhooks
  - Notification preferences per user
  - Notification history and read status

### 3.10 Tenant & User Management Service (`squadron-identity`)

**Purpose:** Manages multi-tenancy, organizations, teams, and user profiles.
Delegates authentication to Keycloak.

- **Responsibilities:**
  - Tenant (organization) CRUD
  - Team management within tenants
  - User profile management (linked to Keycloak identities)
  - Role-based access control (RBAC): `ADMIN`, `TEAM_LEAD`, `DEVELOPER`, `QA`, `VIEWER`
  - Tenant isolation enforcement
  - License/quota management per tenant
  - Multi-tenancy mode is configurable (single-tenant or multi-tenant)

---

## 4. Data Architecture

### 4.1 Database Strategy

- **PostgreSQL 17** as the primary data store
- **One database per service** (database-per-service pattern) -- alternatively,
  **schema-per-service in a shared database** for simpler operations in smaller
  deployments (configurable)
- **PgBouncer** for connection pooling (transaction mode) in front of PostgreSQL
- **HikariCP** as the in-JVM connection pool (Spring Boot default)
- **Flyway** for schema migrations
- **Multi-tenancy:** Row-level tenant isolation using `tenant_id` column on all tables
  + Hibernate filter or PostgreSQL Row Level Security (RLS)

### 4.2 Core Entity Model

```
+--------------+     +--------------+     +--------------+
|   Tenant     |----<|    Team      |----<|    User      |
|              |     |              |     |  (Keycloak)  |
+--------------+     +------+-------+     +------+-------+
                            |                     |
                     +------v-------+     +------v-------+
                     |  Project     |     | Squadron     |
                     |  (mapped to  |     | Config       |
                     |   platform)  |     +--------------+
                     +------+-------+
                            |
                     +------v-------+
                     |    Task      |  <-- synced from ticketing platform
                     +------+-------+
                            |
              +-------------+-------------+
              |             |             |
       +------v------+ +---v----+ +------v------+
       |  Workspace  | | Review | | Conversation |
       |             | |        | | (Agent Chat) |
       +-------------+ +--------+ +--------------+
```

### 4.3 Key Tables

```sql
-- Tenant management (squadron-identity)
CREATE TABLE tenants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    settings        JSONB,
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE teams (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            VARCHAR(255) NOT NULL,
    settings        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    keycloak_id     VARCHAR(255) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255),
    role            VARCHAR(50) NOT NULL,  -- ADMIN, TEAM_LEAD, DEVELOPER, QA, VIEWER
    settings        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE user_teams (
    user_id         UUID NOT NULL REFERENCES users(id),
    team_id         UUID NOT NULL REFERENCES teams(id),
    role            VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    PRIMARY KEY (user_id, team_id)
);

-- Project management (squadron-orchestrator)
CREATE TABLE projects (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    team_id         UUID NOT NULL REFERENCES teams(id),
    name            VARCHAR(255) NOT NULL,
    connection_id   UUID REFERENCES platform_connections(id),
    external_project_id VARCHAR(255),
    repo_url        VARCHAR(1024),
    default_branch  VARCHAR(255) DEFAULT 'main',
    branch_strategy VARCHAR(50) DEFAULT 'TRUNK_BASED',  -- TRUNK_BASED | GITFLOW | CUSTOM
    settings        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Core task table (squadron-orchestrator)
CREATE TABLE tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    team_id         UUID NOT NULL REFERENCES teams(id),
    project_id      UUID NOT NULL REFERENCES projects(id),
    external_id     VARCHAR(255),            -- JIRA key, GH issue #, etc.
    external_url    VARCHAR(1024),
    title           VARCHAR(1024) NOT NULL,
    description     TEXT,
    assignee_id     UUID REFERENCES users(id),
    priority        VARCHAR(50),
    labels          JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Agent conversations (squadron-agent)
CREATE TABLE conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    task_id         UUID NOT NULL,
    user_id         UUID NOT NULL,
    agent_type      VARCHAR(50) NOT NULL,    -- PLANNING | CODING | REVIEW | QA
    provider        VARCHAR(100),
    model           VARCHAR(100),
    status          VARCHAR(50) NOT NULL,    -- ACTIVE | COMPLETED | ABANDONED
    total_tokens    BIGINT DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE conversation_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    role            VARCHAR(20) NOT NULL,    -- SYSTEM | USER | ASSISTANT | TOOL
    content         TEXT,
    tool_calls      JSONB,
    token_count     INTEGER,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Plans (squadron-agent)
CREATE TABLE task_plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    task_id         UUID NOT NULL,
    conversation_id UUID REFERENCES conversations(id),
    version         INTEGER NOT NULL DEFAULT 1,
    plan_content    TEXT NOT NULL,            -- Markdown plan
    status          VARCHAR(50) NOT NULL,     -- DRAFT | APPROVED | SUPERSEDED
    approved_by     UUID,
    approved_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Reviews (squadron-review)
CREATE TABLE reviews (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    task_id         UUID NOT NULL,
    reviewer_id     UUID,                    -- null for AI reviews
    reviewer_type   VARCHAR(20) NOT NULL,    -- HUMAN | AI
    status          VARCHAR(50) NOT NULL,    -- PENDING | APPROVED | CHANGES_REQUESTED | COMMENTED
    comments        JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### 4.4 Redis Usage

| Use Case | Description |
|---|---|
| **Session cache** | Keycloak session tokens, API gateway rate limit counters |
| **Agent response caching** | Cache structured outputs for repeated queries |
| **Distributed locks** | Workspace creation coordination, state machine transitions |
| **Real-time presence** | Track which users are online, which tasks are being worked on |
| **Pub/Sub** | Supplement NATS for WebSocket fan-out to frontend clients |

---

## 5. Security Architecture

### 5.1 Authentication & Authorization

```
+-----------+    OIDC / OAuth2     +----------+
|  Angular  | <------------------> | Keycloak |
|  Frontend |   (Auth Code +       |  26.x    |
+-----+-----+    PKCE flow)       +----+-----+
      |                                 |
      |  Bearer JWT                     | OIDC tokens
      |                                 |
+-----v---------------------------------v-----------+
|              API Gateway                           |
|  - Validates JWT signature (Keycloak JWKS)         |
|  - Extracts tenant_id, user_id, roles              |
|  - Propagates as headers to downstream services    |
+---------------------+-----------------------------+
                      |
           +----------v----------+
           |   Microservices     |
           |   - Spring Security |
           |   - @PreAuthorize   |
           |   - Tenant filter   |
           +---------------------+
```

- **Keycloak 26.x** as the identity provider
- **OAuth 2.0 Authorization Code + PKCE** for the Angular frontend
- **JWT access tokens** propagated through the API Gateway
- **Realm-per-tenant** for strong multi-tenant isolation, OR **single realm with
  groups** for simpler deployments (configurable)
- **RBAC roles:** `squadron-admin`, `team-lead`, `developer`, `qa`, `viewer`
- **Keycloak Identity Brokering** to federate with corporate IdPs (LDAP, SAML, OIDC)

### 5.2 Certificate-Based Security (mTLS)

```
+------------+       +---------------+       +---------------+
|  Vault     |------>| cert-manager  |------>| K8s Secrets   |
|  PKI CA    | issue |  (K8s)        | store | / CSI mount   |
+------------+       +---------------+       +-------+-------+
                                                      | mount
                                              +-------v-------+
                                              | Service Pods  |
                                              | (mTLS enabled)|
                                              +---------------+
```

- **HashiCorp Vault** as the root CA (PKI Secrets Engine)
- **cert-manager** on K8s to automate certificate issuance and rotation
- **Short-lived certificates** (24-72h TTL) for all service-to-service communication
- **Spring Boot SSL Bundles** to consume mounted certificates
- **For Docker deployments:** Vault Agent sidecar for certificate injection
- All inter-service communication over **mTLS**
- External traffic over **TLS 1.3**

### 5.3 Secrets Management

- **HashiCorp Vault** for all secrets: database credentials, API keys, platform tokens,
  encryption keys
- **Spring Cloud Vault** for automatic secret injection into Spring Boot applications
- User platform tokens (JIRA, GitHub, etc.) stored in Vault with user-scoped policies
- Encryption at rest for any secrets stored in PostgreSQL (AES-256-GCM via Vault Transit
  engine)

### 5.4 Delegated User Identity

When Squadron acts on behalf of a user on an external platform:

1. User links their account via OAuth 2.0 flow (Squadron redirects to platform, user
   authorizes, Squadron stores tokens in Vault)
2. On each API call to the platform, Squadron retrieves the **user's own OAuth token**
   from Vault
3. If the token is expired, Squadron uses the refresh token to obtain a new one
4. All operations appear as the user on the external platform
5. If a user revokes access, Squadron gracefully degrades and notifies the user

---

## 6. Inter-Service Communication

### 6.1 Synchronous (REST)

- Service-to-service REST calls via Spring's `RestClient` with mTLS
- **Spring Cloud OpenFeign** for declarative REST clients between services
- Used for: request/response operations (get task details, validate permissions, etc.)

### 6.2 Asynchronous (NATS JetStream)

- **Event-driven** communication for state changes, notifications, long-running operations
- Key event streams:

  | Stream | Description |
  |---|---|
  | `squadron.tasks.state-changed` | Task state transitions |
  | `squadron.agents.invoked` | Agent invocation requests |
  | `squadron.agents.completed` | Agent results |
  | `squadron.workspaces.lifecycle` | Workspace created/destroyed |
  | `squadron.reviews.updated` | Review status changes |
  | `squadron.notifications` | Notification delivery requests |

- **Consumer groups** for load balancing across service instances
- **Durable consumers** for guaranteed delivery
- **Dead letter queue** for failed message processing

### 6.3 Real-Time (WebSocket)

- **STOMP over WebSocket** via Spring WebSocket (through the API Gateway)
- Used for: real-time agent chat streaming, live task status updates, notification push
- Redis Pub/Sub as the WebSocket message broker backend (for multi-instance fan-out)

---

## 7. Frontend Architecture (Angular 21)

### 7.1 Project Structure

```
squadron-ui/
+-- src/
|   +-- app/
|   |   +-- core/                    # Singleton services, guards, interceptors
|   |   |   +-- auth/                # Keycloak auth (angular-auth-oidc-client)
|   |   |   +-- interceptors/        # HTTP interceptors (auth, error handling)
|   |   |   +-- guards/              # Route guards
|   |   +-- shared/                  # Shared components, pipes, directives
|   |   +-- features/
|   |   |   +-- dashboard/           # Main dashboard (task board)
|   |   |   +-- task-board/          # Kanban-style task board
|   |   |   +-- task-detail/         # Task detail with agent interaction
|   |   |   +-- agent-chat/          # Real-time agent conversation UI
|   |   |   +-- review/              # Code review interface (diff viewer)
|   |   |   +-- qa-report/           # QA coverage report viewer
|   |   |   +-- admin/               # Tenant/team admin console
|   |   |   +-- squadron-config/     # Squadron configuration UI
|   |   |   +-- settings/            # User settings, platform connections
|   |   +-- layout/                  # Shell layout, nav, sidebar
|   |   +-- app.routes.ts
|   +-- environments/
+-- angular.json
+-- package.json
+-- tsconfig.json
```

### 7.2 Key Libraries

| Library | Purpose |
|---|---|
| `@angular/material` | UI component library |
| `@ngrx/signals` | State management (Signal Store) |
| `angular-auth-oidc-client` | Keycloak OIDC integration |
| `@stomp/ng2-stompjs` | WebSocket / STOMP for real-time |
| `ngx-monaco-editor` | Code editor / diff viewer for reviews |
| `@swimlane/ngx-charts` | Dashboard charts |
| `marked` / `ngx-markdown` | Markdown rendering (agent responses, plans) |

### 7.3 Key UI Features

- **Task Board:** Kanban-style board reflecting the Squadron workflow states.
  Drag-and-drop to transition (with validation).
- **Agent Chat Panel:** Split-pane view with task context on one side, real-time
  streaming agent chat on the other. Supports markdown rendering, code blocks.
- **Code Review View:** Monaco-based diff viewer showing AI-generated changes alongside
  AI review comments and human review comments.
- **QA Report:** Coverage report visualization (line coverage heatmap), test gap
  analysis, one-click "generate missing tests" action.
- **Squadron Config:** Visual editor for configuring agent assignments, model selection
  per agent type, with live preview of cost estimates.
- **Admin Console:** Tenant management, team management, platform connections, user
  management.

---

## 8. Deployment Architecture

### 8.1 Kubernetes (Production / Preferred)

```yaml
# Namespace structure
squadron-system/          # Core platform services
squadron-workspaces/      # Ephemeral agent workspaces (separate namespace for isolation)
squadron-infra/           # PostgreSQL, Redis, NATS, Keycloak, Vault
```

Each microservice is deployed as:
- `Deployment` (2+ replicas for HA)
- `Service` (ClusterIP)
- `HorizontalPodAutoscaler`
- `PodDisruptionBudget`
- `NetworkPolicy` (restrict ingress/egress)

Infrastructure:
- **Helm charts** for all services (one umbrella chart with sub-charts)
- **Ingress** via NGINX Ingress Controller or Traefik (TLS termination)
- **cert-manager** for automated TLS certificate management
- **Prometheus Operator** + Grafana for monitoring
- **Loki** for log aggregation (lightweight alternative to ELK)

### 8.2 Docker Compose (Development / Small Deployments)

- `docker-compose.yml` for local development and small single-node deployments
- All services + infrastructure in a single compose file
- Docker socket mounted for workspace creation (when using Docker provider)

### 8.3 Air-Gapped Deployment

- All container images published to an **internal registry** (Harbor or any
  OCI-compatible registry)
- Helm chart values for internal registry references
- NATS, PostgreSQL, Redis, Keycloak, Vault all deployable on-prem with no internet
  dependency
- AI models: Self-hosted via Cohere Command-4 on-prem, or Ollama for open-weight models
- Full offline installation bundle: Helm charts + container images as a tarball

---

## 9. Project Structure (Maven Monorepo)

```
squadron/
+-- pom.xml                            # Parent POM (dependency management, plugin management)
+-- squadron-common/                   # Shared DTOs, utilities, security config
|   +-- pom.xml
|   +-- src/main/java/
+-- squadron-gateway/                  # API Gateway (Spring Cloud Gateway)
|   +-- pom.xml
|   +-- src/main/java/
|   +-- src/main/resources/
+-- squadron-orchestrator/             # Task Orchestrator (workflow engine)
|   +-- pom.xml
|   +-- src/main/java/
|   +-- src/main/resources/
|   +-- src/main/resources/db/migration/   # Flyway migrations
+-- squadron-agent/                    # Agent Service (Spring AI)
|   +-- pom.xml
|   +-- src/main/java/
|   +-- src/main/resources/
+-- squadron-workspace/                # Workspace Manager
|   +-- pom.xml
|   +-- src/main/java/
|   +-- src/main/resources/
+-- squadron-platform/                 # Platform Integration (ticketing adapters)
|   +-- pom.xml
|   +-- src/main/java/
|   +-- src/main/resources/
+-- squadron-git/                      # Git operations + platform APIs
|   +-- pom.xml
|   +-- src/main/java/
|   +-- src/main/resources/
+-- squadron-review/                   # Review orchestration
|   +-- pom.xml
|   +-- src/main/java/
|   +-- src/main/resources/
+-- squadron-config/                   # Configuration service
|   +-- pom.xml
|   +-- src/main/java/
|   +-- src/main/resources/
+-- squadron-notification/             # Notification service
|   +-- pom.xml
|   +-- src/main/java/
|   +-- src/main/resources/
+-- squadron-identity/                 # Tenant / User management
|   +-- pom.xml
|   +-- src/main/java/
|   +-- src/main/resources/
+-- squadron-ui/                       # Angular frontend
|   +-- angular.json
|   +-- package.json
|   +-- src/
+-- deploy/
|   +-- docker/
|   |   +-- docker-compose.yml
|   +-- helm/
|   |   +-- Chart.yaml                # Umbrella chart
|   |   +-- charts/
|   |       +-- squadron-gateway/
|   |       +-- squadron-orchestrator/
|   |       +-- ...
|   +-- terraform/                     # Optional: cloud infra provisioning
+-- docs/
|   +-- api-specs/                     # OpenAPI specs per service
|   +-- runbooks/
+-- ARCHITECTURE.md
+-- .github/
    +-- workflows/                     # CI/CD pipelines
```

### 9.1 Maven Parent POM

The parent POM (`pom.xml` at root) manages:

- **Spring Boot parent** via `<parent>` (spring-boot-starter-parent 3.5.3)
- **Dependency management** via `<dependencyManagement>` with BOMs for Spring Cloud,
  Spring AI, and Testcontainers
- **Plugin management** for shared build configuration (compiler, surefire, Spring Boot
  plugin, etc.)
- **Module declarations** for all sub-modules
- **Property-based version management** for all third-party dependencies

### 9.2 Key Maven Dependencies

| Group | Artifact | Version | Purpose |
|---|---|---|---|
| `org.springframework.boot` | `spring-boot-starter-parent` | 3.5.3 | Parent POM |
| `org.springframework.cloud` | `spring-cloud-dependencies` | 2025.0.0 | Cloud BOM |
| `org.springframework.ai` | `spring-ai-bom` | 1.0.0 | AI BOM |
| `org.keycloak` | `keycloak-admin-client` | 26.0.5 | Keycloak admin API |
| `io.nats` | `jnats` | 2.21.1 | NATS client |
| `io.kubernetes` | `client-java` | 19.0.3 | Kubernetes API client |
| `com.github.docker-java` | `docker-java` | 3.5.1 | Docker API client |
| `org.postgresql` | `postgresql` | 42.7.7 | PostgreSQL JDBC driver |
| `org.flywaydb` | `flyway-core` | 11.8.2 | Database migrations |
| `org.flywaydb` | `flyway-database-postgresql` | 11.8.2 | Flyway PostgreSQL support |
| `io.micrometer` | `micrometer-registry-prometheus` | (managed) | Prometheus metrics |
| `org.testcontainers` | `testcontainers-bom` | 1.21.3 | Testcontainers BOM |
| `org.springdoc` | `springdoc-openapi-starter-webmvc-ui` | 2.8.6 | OpenAPI / Swagger UI |
| `org.springframework.cloud` | `spring-cloud-starter-vault-config` | (managed) | Vault integration |

---

## 10. Implementation Phases

### Phase 1: Foundation (Weeks 1-4)

**Goal:** Project scaffolding, infrastructure, authentication, and basic task board.

1. **Project setup:** Maven monorepo, Spring Boot 3.5.x, shared dependencies, CI pipeline
2. **Infrastructure:** Docker Compose with PostgreSQL, Redis, NATS, Keycloak, Vault
3. **`squadron-common`:** Shared DTOs, security config, tenant context, NATS event models
4. **`squadron-identity`:** Keycloak integration, tenant/team/user management, RBAC
5. **`squadron-gateway`:** API Gateway with JWT validation, routing
6. **`squadron-config`:** Hierarchical configuration store
7. **`squadron-ui` scaffold:** Angular 21 app with Keycloak login, Material shell layout,
   basic routing
8. **Flyway migrations** for core schema

**Deliverable:** Authenticated users can log in, see teams, and access a basic dashboard.

### Phase 2: Task Management & Platform Integration (Weeks 5-8)

**Goal:** Ticketing platform integration and the task board workflow.

1. **`squadron-platform`:** Adapter interface + all platform adapters:
   - JIRA Cloud adapter (REST API v3 + OAuth 2.0 3LO)
   - JIRA Server/DC adapter (REST API v2 + PAT/Basic Auth)
   - GitHub Issues adapter (REST/GraphQL + GitHub App/OAuth)
   - GitLab Issues adapter (REST API v4 + OAuth2)
   - Azure DevOps adapter (REST API + OAuth2/PAT)
2. **`squadron-orchestrator`:** Custom PostgreSQL state machine, workflow definitions,
   state transitions
3. **Task sync:** Import tasks from ticketing platforms, bidirectional status sync
4. **`squadron-ui` task board:** Kanban board with drag-and-drop state transitions
5. **User-delegated identity:** OAuth flows for platform account linking
6. **Webhook receivers:** Real-time sync from platforms

**Deliverable:** Tasks sync from ticketing platforms. Users can view and transition tasks
through the workflow.

### Phase 3: Workspaces & Agent Core (Weeks 9-14)

**Goal:** Sandboxed workspaces and the first AI agent (planning).

1. **`squadron-workspace`:** Kubernetes provider (pod creation, exec, file copy, cleanup)
2. **`squadron-workspace`:** Docker provider (fallback)
3. **`squadron-agent`:** Spring AI integration, multi-provider config, conversation
   management
4. **Planning agent:** Ticket description + codebase context -> implementation plan
5. **Agent chat UI:** Real-time streaming conversation with the planning agent via
   WebSocket
6. **Plan approval workflow:** Developer refines plan, marks as approved
7. **`squadron-git`:** Clone repos into workspaces, branch management

**Deliverable:** Developer moves task to Planning -> workspace spins up -> planning agent
proposes a plan -> developer refines via chat -> approves plan.

### Phase 4: Code Generation & Git Integration (Weeks 15-19)

**Goal:** Coding agent that implements approved plans.

1. **Coding agent:** Takes approved plan + codebase context -> generates code changes
2. **Tool calling:** Agent can read/write files, run commands, run tests inside workspace
3. **`squadron-git`:** Commit changes, push branch, create PR/MR
4. **Git platform adapters:** GitHub, GitLab, Bitbucket PR creation
5. **Branch strategy configuration:** GitFlow support, trunk-based, custom
6. **Diff viewer UI:** Show generated code changes before creating PR

**Deliverable:** Developer moves task to Propose Code -> coding agent generates code ->
creates PR on the Git platform.

### Phase 5: Review & QA (Weeks 20-24)

**Goal:** AI-assisted code review and QA with coverage analysis.

1. **`squadron-review`:** Review policy engine, multi-reviewer orchestration
2. **Review agent:** Automated code review with structured feedback
3. **Review UI:** Monaco diff viewer with inline AI + human comments
4. **QA agent:** Test coverage analysis, test gap detection, test generation
5. **Coverage tooling:** Integration with JaCoCo (Java), Istanbul/NYC (JS/TS),
   coverage.py (Python) -- run inside workspace
6. **QA report UI:** Coverage visualization, test gap report
7. **Auto-transition guards:** Require all reviews approved + QA passed before merge

**Deliverable:** Full review and QA pipeline. AI reviews code, QA agent analyzes coverage
and generates missing tests.

### Phase 6: Merge, Notifications & Polish (Weeks 25-28)

**Goal:** Merge automation, notifications, and production hardening.

1. **Merge step:** Auto-merge when all gates pass, conflict detection, merge strategy
   configuration
2. **`squadron-notification`:** Email, Slack/Teams webhook, in-app notifications
3. **Squadron configuration UI:** Full visual editor for agent configurations
4. **Admin console:** Tenant management, usage dashboards, cost tracking
5. **Token usage tracking:** Per-user, per-team, per-tenant cost attribution
6. **Error handling & resilience:** Circuit breakers, retry policies, dead letter queues

**Deliverable:** Complete end-to-end workflow from ticket to merge.

### Phase 7: Scale, Security Hardening & Air-Gap (Weeks 29-34)

**Goal:** Production readiness at scale.

1. **Helm charts:** Production-grade Helm charts for all services
2. **mTLS:** Vault PKI + cert-manager for all inter-service communication
3. **Air-gap packaging:** Offline installation bundle, internal registry support
4. **Load testing:** Simulate 1000+ concurrent users, tune PostgreSQL/PgBouncer, tune NATS
5. **Horizontal scaling:** HPA policies, workspace pool sizing
6. **Audit logging:** Comprehensive audit trail for all operations
7. **Security scanning:** Container image scanning, dependency vulnerability scanning
8. **Documentation:** Architecture docs, API docs (OpenAPI), runbooks, user guides

**Deliverable:** Production-ready, scalable, secure deployment for thousands of developers.

---

## 11. Key Architectural Decisions Summary

| # | Decision | Choice | Rationale |
|---|---|---|---|
| 1 | Backend framework | Spring Boot 3.5.x | Stable, extensive ecosystem, Spring AI compatibility |
| 2 | AI framework | Spring AI 1.0.0 | Native Spring integration, model portability, auto-config |
| 3 | Frontend | Angular 21 | Per requirements, Signals for reactivity |
| 4 | Database | PostgreSQL 17 + PgBouncer | ACID, JSONB flexibility, battle-tested, air-gap friendly |
| 5 | Cache | Redis 7 | Distributed cache, sessions, locks, pub/sub |
| 6 | Message broker | NATS + JetStream | Minimal ops, air-gap friendly, high throughput |
| 7 | Workflow engine | Custom PostgreSQL state machine | Full control, no extra infra, simple to understand and extend |
| 8 | Identity | Keycloak 26.x | Open-source SSO/OIDC, LDAP federation, CNCF project |
| 9 | Secrets | HashiCorp Vault | PKI CA, secret storage, encryption, K8s native |
| 10 | Sandboxes | K8s pods (primary) + Docker (fallback) | Configurable, scalable, isolated |
| 11 | Git operations | Git CLI via ProcessBuilder | Simplicity, full git feature set, no library limitations |
| 12 | Ticketing | Adapter pattern, direct REST clients | No unmaintained dependencies, full API control |
| 13 | Deployment | Helm + Docker Compose | K8s for prod, Compose for dev, air-gap via image bundles |
| 14 | Tenancy | Configurable (single / multi) | Row-level isolation with `tenant_id`, realm-per-tenant in Keycloak |
| 15 | Build tool | Maven | Multi-module monorepo with parent POM dependency management |
| 16 | Git platforms | GitHub + GitLab + Bitbucket | Cover the major Git hosting platforms for PR/MR operations |
| 17 | Ticketing platforms | JIRA Cloud + Server, GitHub, GitLab, Azure DevOps | Broad coverage from day one via adapter pattern |
| 18 | Monitoring | Prometheus + Grafana | Industry standard, native Spring Boot Actuator support |
