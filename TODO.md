# Squadron - Implementation Progress Tracker

**Last updated:** 2026-04-20
**Current Status:** All 11 modules fully implemented with tests. All post-launch features complete (Features 1-24). Feature 24: Ticketless Tasks — create tasks directly from UI without external tickets, distinct "Ticketless" column on task board, agent interaction (plan/build modes), full backend + frontend implementation. **All 4,328 tests passing (0 failures, 0 errors). Angular build passing.**

---

## Completed Modules

### squadron-common (66 src / 66 test)
- [x] DTOs (TaskDto, TenantDto, TeamDto, UserDto, ProjectDto, etc.)
- [x] Events (TaskStateChanged, AgentInvoked, AgentCompleted, ReviewUpdated, etc.)
- [x] Security (TenantContext, TenantFilter, JwtService, TokenEncryption, AccessLevel)
- [x] Exceptions (Global handler, custom exceptions)
- [x] NATS config (NatsConfig, NatsEventPublisher with JetStream support, JetStreamConfig, JetStreamSubscriber)
- [x] Feign config (FeignConfig, FeignErrorDecoder)
- [x] Jackson config
- [x] Resilience (CircuitBreaker, RetryHelper, ResilientClient)
- [x] Audit system (AuditService using NatsEventPublisher, AuditAspect, AuditController, etc.)
- [x] Utilities (JsonUtils, SlugUtils)
- [x] All tests passing

### squadron-gateway (11 src / 11 test)
- [x] GatewayConfig with service routes + WebSocket routes
- [x] SecurityConfig (JWT validation)
- [x] CorsConfig
- [x] Filters (RequestLogging, TenantHeader, RateLimit, WebSocketToken)
- [x] HealthStatusController
- [x] Agent dashboard route (15 routes total)
- [x] Agent squadron route (no stripPrefix, forwards full path)
- [x] Platform-service route (no stripPrefix, forwards full path)
- [x] Tenant-service route for `/api/tenants/**`
- [x] WebSocketTokenFilter: extracts JWT from `?access_token=` query param on WS upgrade, injects as Bearer header
- [x] All tests passing (16 routes total, 12 WebSocket filter tests)

### squadron-identity (42 src / 42 test)
- [x] Tenant/Team/User CRUD
- [x] Auth providers (Keycloak, LDAP, OIDC)
- [x] Security groups and permissions
- [x] AuthProviderConfig management
- [x] TenantController: `GET /api/tenants/current` and `PATCH /api/tenants/current/settings` (JWT-based tenant lookup)
- [x] TenantService: `updateTenantSettings()` with merge-style partial update, proper JSON via ObjectMapper
- [x] UserController: `GET /api/users/{userId}/preferences` and `PATCH /api/users/{userId}/preferences` (language persistence)
- [x] UserService: `getUserPreferences()` and `updateUserPreferences()` with JSONB merge-style update
- [x] Flyway migrations (V1, V2)
- [x] All tests passing (including 4 new UserController tests, 6 new UserService tests)

### squadron-orchestrator (39 src / 36 test)
- [x] Custom PostgreSQL state machine (WorkflowEngine)
- [x] Task/Project/Workflow CRUD
- [x] State transitions with validation (9 states: BACKLOG, PRIORITIZED, PLANNING, PROPOSE_CODE, IN_PROGRESS, REVIEW, QA, MERGE, DONE)
- [x] TaskSyncService
- [x] DefaultWorkflowInitializer
- [x] PlatformServiceClient (Feign)
- [x] ResilientPlatformServiceClient (circuit breaker + retry wrapper)
- [x] Project workflow mappings (entity, repository, service, controller endpoints)
- [x] `branchNamingTemplate` field on `Project` entity and `CreateProjectRequest` DTO
- [x] Flyway migrations (V1, V2, V3, V4)
- [x] All 401 tests passing

### squadron-agent (98 src / 99 test)
- [x] Agent providers (OpenAI-compatible, Ollama)
- [x] Tool system (ToolRegistry, ToolExecutionEngine, built-in tools)
- [x] Services (Agent, Planning, Coding, Review, QA, Merge, Coverage)
- [x] Conversation management (ConversationSummaryDto, getConversationSummaries, getActiveConversationForTask)
- [x] Squadron config management
- [x] Token usage tracking
- [x] WebSocket controller
- [x] Agent dashboard API (DTOs, service, controller, 17 tests)
- [x] User agent squadron configuration (entity, DTO, repository, service, controller, migration, 28 tests)
- [x] Agent squadron overhaul: per-agent hosting_type/provider/model/baseUrl/apiKeyRef/description fields
- [x] Flyway V4 migration: adds hosting_type, base_url, api_key_ref, description columns to user_agent_configs
- [x] Default agents with provider/model assignments (Sol=github-copilot/claude-sonnet-4, Titan=github-copilot/gpt-4o, etc.)
- [x] Auto-description generation (humanizeProvider + humanizeModel → "Claude Sonnet 4 via GitHub Copilot")
- [x] Listeners migrated to JetStreamSubscriber; unified TaskStateDispatcher replaces 5 individual listeners (PlanApproval kept separate)
- [x] Feign clients (OrchestratorClient, GitServiceClient, ReviewServiceClient, WorkspaceServiceClient)
- [x] Resilient Feign wrappers (circuit breaker + retry for all 4 Feign clients)
- [x] Flyway migrations (V1, V2, V3, V4, V5)
- [x] All tests passing (986 tests)

### squadron-workspace (20 src / 19 test)
- [x] Workspace providers (Kubernetes, Docker)
- [x] WorkspaceService with lifecycle management
- [x] WorkspaceGitService (HTTPS + SSH support with GIT_SSH_COMMAND)
- [x] WorkspaceGitService.testGitAccess() — container-based `git ls-remote` using `workspaceProvider.createContainer()` + `exec()` + `destroyContainer()`
- [x] WorkspaceCleanupScheduler
- [x] PlatformServiceClient (Feign) + ResilientPlatformServiceClient (circuit breaker + retry)
- [x] DockerWorkspaceProvider defaults to `squadron/workspace-base:latest` image (git pre-installed)
- [x] Flyway migration (V1)
- [x] All 229 tests passing

### squadron-platform (36 src / 36 test)
- [x] Adapter pattern with registry
- [x] JIRA Cloud adapter
- [x] JIRA Server adapter
- [x] GitHub Issues adapter
- [x] GitLab Issues adapter
- [x] Azure DevOps adapter
- [x] OAuth2 token management
- [x] Webhook processing
- [x] Platform sync service
- [x] Project statuses endpoint (GET /api/platforms/connections/{id}/statuses)
- [x] Remote projects endpoint (GET /api/platforms/connections/{id}/projects) — `getProjects()` on all 5 adapters
- [x] `PlatformProjectDto` (key, name, description, url, avatarUrl)
- [x] SSH key management: `SshKey` entity, `SshKeyService`, `SshKeyController` at `/api/platforms/ssh-keys` (full CRUD)
- [x] SSH key private-key endpoint: `GET /api/platforms/ssh-keys/{id}/private-key` (returns decrypted key for inter-service use)
- [x] `platform_category` column on `platform_connections` (TICKET_PROVIDER / GIT_REMOTE), auto-determined by platform type
- [x] `PlatformConnectionService.listConnectionsByTenantAndCategory()` + `GET /tenant/{tenantId}/category/{category}` endpoint
- [x] `UpdateConnectionRequest` DTO for partial updates without tenantId constraint
- [x] Flyway migrations (V1, V2, V3, V4, V5, V6)
- [x] All 536 tests passing

### squadron-git (34 src / 36 test)
- [x] Git platform adapters (GitHub, GitLab, Bitbucket)
- [x] Git operations service
- [x] Branch strategy management
- [x] PR/MR management
- [x] Diff service
- [x] Flyway migrations (V1, V2, V3)
- [x] All tests passing

### squadron-review (26 src / 27 test)
- [x] Review service with policy engine
- [x] Review gate service
- [x] QA report management
- [x] Flyway migrations (V1, V2)
- [x] All tests passing

### squadron-config (11 src / 11 test)
- [x] Hierarchical configuration (tenant > team > user)
- [x] ConfigController
- [x] Flyway migration (V1)
- [x] All tests passing

### squadron-notification (24 src / 24 test)
- [x] Notification channels (Email, Slack, Teams, InApp)
- [x] NATS event listeners migrated to JetStreamSubscriber (4 durable subscriptions)
- [x] Notification preferences
- [x] Retry service
- [x] Flyway migrations (V1, V2)
- [x] All tests passing

### squadron-ui (Angular 21) — 1,137 tests passing
- [x] 33 components (dashboard, tasks, projects, reviews, agent-chat, squadron-config, user-tokens, etc.)
- [x] 25 services (including agent-dashboard, user-squadron, user-token, ssh-key, platform services)
- [x] 15 models (including agent dashboard, squadron config, user-token, RemoteProject, SshKey, ConversationSummary interfaces)
- [x] Auth infrastructure (guard, interceptor with token refresh on 401, OIDC)
- [x] Shared components (header, sidebar, avatar, notification-bell)
- [x] Admin console (users, teams, security groups, permissions, etc.)
- [x] Project config rewritten as 4-step setup wizard: Ticket Providers → Git Remotes + SSH Keys → Projects → Branch & Workflow
- [x] Agent-focused dashboard redesign (active/idle agents, active work, timeline, type breakdown)
- [x] Task detail agent panel (live progress, conversation history, agent session status, "Open Chat" navigation)
- [x] Ticket provider integration UI (connection linking, remote status fetch, status-aware mappings)
- [x] User agent squadron configuration UI (agent cards, add/edit/remove/reset, inline template)
- [x] Agent squadron overhaul UI: hosting type badges (Cloud/Local/Custom), provider+model cascading dropdowns, PROVIDER_CATALOG with 6 providers, auto-description generation, base URL/API key fields for self-hosted/custom
- [x] Celestial body default agent names (Planner→Sol, Coder→Titan, Reviewer→Vega, QA Tester→Comet, Merger→Pulsar, Coverage Analyst→Quasar, Coder 2→Nova, Reviewer 2→Nebula)
- [x] Unified settings page: 6-tab layout (General, Providers & Projects, Agent Squadron, Notifications, Agent Config, Platform Tokens)
- [x] User Platform Tokens tab: link/unlink PAT and OAuth2 accounts, view linked accounts per user
- [x] Project config labels generalized from ticket-specific to platform-inclusive
- [x] Project card headers enhanced with platform type badge, connection status indicator, improved mapping labels
- [x] Provider editing: Edit buttons on ticket provider and git remote cards, edit/update forms, optional credential update
- [x] Translation key audit: 168 missing keys added across en.json and fr.json

### Infrastructure
- [x] Docker Compose (docker-compose.yml)
- [x] Parent POM with dependency management
- [x] All 25 Flyway migrations (V1-V8 for platform, V1-V4 for orchestrator, V1-V4 for agent, V1-V3 for identity/git, V1-V2 for review/notification, V1 for config/workspace)
- [x] Test LDAP integration (docker-compose-testldap.yml, seed data)
- [x] Jira Server test instance (docker-compose-testldap.yml, Flyway V7 seed, setup instructions)
- [x] GitLab CE test instance (docker-compose-testldap.yml, LDAP pre-configured, Flyway V8 seed, setup instructions)
- [x] Database provisioning fix: `ensure_databases()` creates missing DBs on existing PostgreSQL volumes
- [x] All 20 containers healthy with testldap-build-and-start.sh
- [x] Gateway healthcheck fix: uses `/actuator/health/liveness` (doesn't aggregate downstream services)
- [x] Gateway SecurityConfig: permits `/actuator/health/**` sub-paths (liveness/readiness probes)
- [x] Gateway application.yml: liveness/readiness probes enabled by default
- [x] PostgreSQL max_connections increased to 300 (supports 10+ services with HikariCP pools)
- [x] CredentialClient constructor fix: `@Autowired` annotation for Spring DI

---

## Completed Inter-Service Communication

### OpenFeign Clients
- [x] FeignConfig + FeignErrorDecoder in squadron-common
- [x] OrchestratorClient (squadron-agent -> squadron-orchestrator)
- [x] GitServiceClient (squadron-agent -> squadron-git)
- [x] ReviewServiceClient (squadron-agent -> squadron-review)
- [x] WorkspaceServiceClient (squadron-agent -> squadron-workspace)
- [x] PlatformServiceClient (squadron-orchestrator -> squadron-platform)
- [x] PlatformServiceClient (squadron-workspace -> squadron-platform) — SSH key retrieval for git operations
- [x] Feign URL properties configured in all application.yml files
- [x] Resilient wrappers with circuit breaker + retry for all 6 Feign clients

### NATS JetStream
- [x] JetStreamConfig (10 durable streams: TASKS, AGENTS, WORKSPACES, REVIEWS, GIT_EVENTS, NOTIFICATIONS, CONFIG, PLATFORM, AUDIT, COVERAGE)
- [x] JetStreamSubscriber utility (durable subscribe with ack/nak, fallback to core NATS)
- [x] NatsEventPublisher upgraded (JetStream-first publish with core NATS fallback, plus publishRaw for non-event payloads)
- [x] All 7 NATS listeners migrated to JetStreamSubscriber
- [x] AuditService migrated to use NatsEventPublisher.publishRaw() instead of raw Connection.publish()
- [x] NATS subject naming standardized (squadron.tasks.*, squadron.agents.*, etc.)

---

## Remaining Work

### Phase 6: Integration & Polish
- [x] End-to-end workflow testing (task lifecycle across services)
- [x] Error handling improvements (circuit breakers on Feign clients)
- [x] WebSocket integration testing
- [x] Cross-service event flow validation

### Phase 7: Deployment & Hardening
- [x] Helm charts for all services
- [x] Kubernetes manifests
- [x] mTLS configuration
- [x] Production application.yml profiles
- [x] Health checks and readiness probes
- [x] Prometheus metrics endpoints
- [x] API documentation (OpenAPI specs)

### Phase 8: Post-Launch Features
- [x] Project workflow mappings (backend + frontend)
  - `project_workflow_mappings` table mapping internal TaskState to external platform statuses
  - Full CRUD via ProjectController endpoints
  - Frontend settings page at `/settings/projects`
- [x] Agent dashboard redesign (backend + frontend)
  - `GET /api/agents/dashboard` endpoint with aggregated agent metrics
  - Dashboard shows: active/idle agents, active work cards, recent activity timeline, agent type breakdown
  - Gateway route `agent-dashboard` (14 routes total)
  - Mock data fallback on API error
- [x] Ticket provider integration (Feature 1 - backend + frontend)
  - Backend: `GET /api/platforms/connections/{id}/statuses?projectKey={key}` endpoint
  - Backend: `fetchProjectStatuses()` in PlatformConnectionService (configures adapter, calls getAvailableStatuses)
  - Backend: 7 new tests (3 controller + 4 service)
  - Gateway: platform-service route changed to NOT strip prefix (fixes routing)
  - Frontend: Platform connection linking per project (connection dropdown + external project key input)
  - Frontend: Remote status fetching with mock fallback per platform type
  - Frontend: Status-aware workflow mapping (dropdown when statuses fetched, free text otherwise)
  - Frontend: 22 tests (10 original + 12 new for platform integration)

---

## Remaining Features

### Feature 2: Agent Interaction UI (OpenCode-inspired)
- [x] Backend: Agent conversation WebSocket enhancements (live prompting, cancel/interrupt)
- [x] Backend: Agent TODO/progress tracking via NATS events
- [x] Frontend: Agent interaction page with live prompting during execution
- [x] Frontend: Cancel/interrupt capability for running agents
- [x] Frontend: Real-time TODO/progress visibility panel
- [x] Tests for all new backend + frontend components
  - Backend: AgentSessionManager (14 tests), AgentProgressDto (8 tests), AgentInterruptRequest (6 tests)
  - Backend: AgentWebSocketController (+3 tests), AgentChatController (+6 tests)
  - Frontend: AgentService (16 tests), AgentChatComponent (25 tests)

### Feature 3: Notification System
- [x] Backend: NATS event listeners for significant events (agent completion, errors, interrupts)
- [x] Backend: Push events via WebSocket to UI (InAppNotificationChannel via STOMP)
- [x] Frontend: Toast/popup notification component (slides from top-down)
- [x] Frontend: Notification bell with live WebSocket connection
- [x] Tests for all new backend + frontend components
  - Frontend: NotificationService (21 tests), NotificationBellComponent (18 tests), NotificationToastComponent (8 tests)

### Feature 4: Deployment Documentation
- [x] Self-hosted deployment guide (docs/deployment/self-hosted.md — 1136 lines)
- [x] On-premise deployment guide (docs/deployment/on-premise.md — 684 lines)
- [x] Cloud/Azure AKS deployment guide (docs/deployment/azure-aks.md — 1543 lines)

### Feature 5: User Agent Squadron Configuration
- [x] Backend: UserAgentConfig entity (per-user, per-agent row with UUID, tenantId, userId, agentName, agentType, etc.)
- [x] Backend: UserAgentConfigDto with Jakarta validation
- [x] Backend: Flyway migration V3 (user_agent_configs table + indexes)
- [x] Backend: UserAgentConfigRepository (JPA queries for tenant+user)
- [x] Backend: UserAgentConfigService (auto-seeding 8 defaults, CRUD, validation, max count enforcement)
- [x] Backend: UserAgentConfigController at /api/agents/squadron (GET, POST, PUT, DELETE, POST /reset, GET /limits)
- [x] Backend: application.yml config squadron.agents.max-per-user
- [x] Backend: 18 service tests + 10 controller tests (all passing)
- [x] Gateway: agent-squadron route (no stripPrefix, before catch-all agent-service route)
- [x] Gateway: GatewayConfigTest updated (15 routes, new test)
- [x] Frontend: squadron-config.model.ts (UserAgentConfig, AGENT_TYPES, SquadronLimits)
- [x] Frontend: UserSquadronService (extends ApiService, CRUD + unwrap ApiResponse.data)
- [x] Frontend: SquadronConfigComponent (inline template+styles, signals, agent cards)
- [x] Frontend: Route at /settings/squadron, sidebar nav item "My Squadron"
- [x] Frontend: 9 service tests + 18 component tests (all passing)
- [x] Tests: 684 Angular tests passing, all backend tests passing

### Feature 10: Unified Settings Page (Settings UI Cleanup)
- [x] Consolidated 4 separate settings routes into single `/settings` page with 6 tabs
- [x] Tabs: General, Providers & Projects, Agent Squadron, Notifications, Agent Config, Platform Tokens
- [x] SettingsComponent rewritten as tabbed container importing all sub-components
- [x] Removed separate routes: `/settings/projects`, `/settings/squadron`, `/settings/notifications`, `/settings/agent-config`
- [x] Removed admin duplicate: `/admin/platforms` route removed
- [x] Sidebar cleanup: removed "Providers" and "My Squadron" nav items (5 nav items, 5 admin items)
- [x] Sidebar icon cases cleaned up (removed unused `platforms`/`agents` from regular nav, `platforms` from admin nav)
- [x] Settings spec rewritten: 21 tests (tab system, sub-component rendering, general tab behavior, DOM rendering, platform tokens tab)
- [x] Sidebar spec updated: 5 nav items, 10 total with admin
- [x] All 756 Angular tests passing, all backend tests passing, 19 containers healthy

### Feature 6: Auth Interceptor Token Refresh
- [x] Auth interceptor: On 401, attempt `refreshToken()` before logging out
- [x] Auth interceptor: Retry original request with new token on successful refresh
- [x] Auth interceptor: Skip refresh for `/auth/refresh` and `/auth/login` URLs (prevent infinite loops)
- [x] Auth interceptor: 12 tests (was 9, added 3 refresh scenarios)

### Feature 7: Project Config Page Redesign (Providers-First Flow)
- [x] Sidebar: Renamed "Project Config" → "Providers", icon changed to 'platforms'
- [x] Two-tab layout: Providers tab (default) + Projects tab
- [x] Providers tab: List connections, add provider form with dynamic credential fields per platform/auth type
- [x] Providers tab: AUTH_TYPE_OPTIONS mapping (JIRA: API Token/PAT, GitHub: PAT/App, GitLab: PAT/OAuth, Azure DevOps: PAT/OAuth, Bitbucket: App Password/OAuth)
- [x] Projects tab: "Add provider first" guard when no connections exist
- [x] Projects tab: New project form requires selecting a ticket provider connection
- [x] Removed all mock/hardcoded project fallback data — empty lists on API error
- [x] Frontend: CreateConnectionRequest interface in security.model.ts
- [x] Frontend: PlatformService.createConnectionFromRequest() method
- [x] Tests: 37 component tests (was 22), 10 platform service tests (was 8)

### Feature 8: Whimsical Agent Names
- [x] Backend: Default agent names changed (Planner→Architect, Coder→Maverick, Reviewer→Hawkeye, QA Tester→Gremlin, Merger→Stitch, Coverage Analyst→Radar, Coder 2→Phoenix, Reviewer 2→Oracle)
- [x] Backend: UserAgentConfigService + controller tests updated
- [x] Frontend: user-squadron.service.spec.ts + squadron-config.component.spec.ts updated

### Feature 9: Backend/Frontend Schema Alignment & Security Fixes
- [x] Flyway V4 migration: `V4__add_name_to_platform_connections.sql` — adds `name VARCHAR(255)` column, backfills existing rows, sets NOT NULL
- [x] `PlatformConnection` entity: added `name` field with `@Column(nullable = false, length = 255)`
- [x] `CreateConnectionRequest` DTO: added `@NotBlank name` field
- [x] `ConnectionInfoResponse` DTO: added `name` field, updated `fromEntity()` mapper
- [x] `PlatformConnectionService`: create/update now handle `name`
- [x] `PlatformConnectionController`: rewritten to return `ConnectionInfoResponse` instead of raw `PlatformConnection` entity (prevents credential exposure)
- [x] Frontend `ConnectionStatus` enum: changed from `CONNECTED/DISCONNECTED/ERROR` to `ACTIVE/ERROR` to match backend
- [x] Frontend `PlatformConnection` interface: added optional `authType?` field
- [x] Sidebar icons: added `platforms` (code brackets) and `agents` (robot) cases to regular navItems `@switch`
- [x] Flyway V3 migration for orchestrator: `V3__make_team_id_nullable.sql` — makes `team_id` nullable on projects/tasks
- [x] Orchestrator `Project` entity and `CreateProjectRequest` DTO: `teamId` now optional
- [x] JWT security configs: squadron-identity and squadron-platform SecurityConfig updated to use internal JWKS endpoint
- [x] Flaky JWT tamper test fix: `SquadronJwtServiceTest.should_throwSecurityException_when_tokenIsTampered` now reliably corrupts signature
- [x] All backend tests updated (controller, service, DTO, entity, repository integration tests)
- [x] All 684 Angular tests passing, all backend tests passing (BUILD SUCCESS)

### Bug Fix: Hibernate jsonb Type Mismatch (HTTP 500 on Provider Save)
- [x] Root cause: PostgreSQL `column "X" is of type jsonb but expression is of type character varying` — Hibernate binding JSONB fields as VARCHAR
- [x] Fix: Added `@JdbcTypeCode(SqlTypes.JSON)` annotation to all 27 jsonb fields across 17 entity files
- [x] Affected entities (15 fields fixed, 12 already correct):
  - `PlatformConnection` — `credentials`, `metadata`
  - `UserPlatformToken` — `tokenMetadata`
  - `ConversationMessage` — `toolCalls`
  - `SquadronConfig` — `config`
  - `ConfigAuditLog` — `previousValue`, `newValue`
  - `ConfigEntry` — `configValue`
  - `GitOperation` — `details`
  - `Team` — `settings`
  - `Tenant` — `settings`
  - `AuthProviderConfig` — `config`
  - `NotificationPreference` — `mutedEventTypes`
  - `QAReport` — `findings`, `testGaps`, `coverageDetails`
  - `ReviewPolicy` — `autoRequestReviewers`, `reviewChecklist`

### Security Fix: apiToken Credential Encryption
- [x] `PlatformConnectionService.SENSITIVE_CREDENTIAL_KEYS` was missing `"apiToken"` — only had `"apiKey"`
- [x] When using Jira Cloud "API Token" auth, credential key `"apiToken"` was stored unencrypted
- [x] Added `"apiToken"` to `SENSITIVE_CREDENTIAL_KEYS` set
- [x] Added `"apiToken"` to `getDecryptedAccessToken()` lookup list

### Feature 11: JIRA Cloud vs JIRA Server Split
- [x] Backend already had separate adapters: `JiraCloudAdapter` (REST API v3) and `JiraServerAdapter` (REST API v2)
- [x] Frontend `PlatformConnectionType` enum: replaced `JIRA` with `JIRA_CLOUD` + `JIRA_SERVER`
- [x] Frontend `PlatformType` enum: replaced `JIRA` with `JIRA_CLOUD` + `JIRA_SERVER`
- [x] `AUTH_TYPE_OPTIONS` split: JIRA_CLOUD gets API Token + OAuth 2.0; JIRA_SERVER gets PAT + Basic Auth
- [x] `platformIcon()` updated in both components: "Jira Cloud" and "Jira Server / DC"
- [x] `getMockStatuses()` updated for new types
- [x] Mock data and `newProviderForm()` default updated
- [x] `RepositoryIntegrationTest` updated: all `"JIRA"` → `"JIRA_CLOUD"`, plus multi-type query test
- [x] Flyway V5 migration: `V5__migrate_jira_to_jira_cloud.sql` — converts existing `"JIRA"` rows to `"JIRA_CLOUD"`
- [x] All frontend test files updated (project-config, platform-connections, platform.service specs)
- [x] `WebhookProcessingService` already handles backward compat (searches both JIRA_CLOUD and JIRA_SERVER)

### Bug Fix: 404 on `/api/tenants/current`
- [x] Root cause 1: No gateway route for `/api/tenants/**` — added `tenant-service` route in `GatewayConfig.java`
- [x] Root cause 2: No `/current` endpoint — `TenantController` only had `@GetMapping("/{id}")`
- [x] Fix: Added `GET /api/tenants/current` and `PATCH /api/tenants/current/settings` endpoints using `SecurityContextHolder` JWT extraction
- [x] `TenantService.updateTenantSettings()` — merge-style partial update with proper JSON via ObjectMapper
- [x] `TenantService.toDto()` — now maps settings as `Map<String, Object>`
- [x] Tests: 3 new TenantController tests, 5 new TenantService tests, GatewayConfigTest updated (16 routes)

### Bug Fix: 500 on `POST /api/projects`
- [x] Root cause 1: Role mismatch — JWT has `"roles":["developer"]` but endpoint required `squadron-admin` or `team-lead`
- [x] Root cause 2: Missing `AccessDeniedException` handler in GlobalExceptionHandler
- [x] Root cause 3: Frontend didn't send `tenantId` — controller extracts from JWT if not provided
- [x] Root cause 4: Missing `MethodArgumentNotValidException` handler
- [x] Fix: Broadened `@PreAuthorize` to include `developer`, added exception handlers (403 + 400), made tenantId optional in DTO, controller uses SecurityContextHolder
- [x] Tests: 1 new ProjectController test, 2 new GlobalExceptionHandler tests

### Feature 12: Redesigned "Add Project" UI (Import from Remote Provider)
- [x] Backend: `PlatformProjectDto` (key, name, description, url, avatarUrl) — new DTO
- [x] Backend: `getProjects()` added to `TicketingPlatformAdapter` interface — returns `List<PlatformProjectDto>`
- [x] Backend: Implemented `getProjects()` in all 5 adapters (JiraCloud, JiraServer, GitHub, GitLab, AzureDevOps)
- [x] Backend: `fetchProjects(UUID connectionId)` added to `PlatformConnectionService`
- [x] Backend: `GET /api/platforms/connections/{id}/projects` endpoint added to controller
- [x] Backend: 25 new tests (4 service, 3 controller, 15 adapter tests across 5 adapters, 3 interface tests)
- [x] Backend: All 474 platform tests passing
- [x] Frontend: `RemoteProject` interface in `project.model.ts`
- [x] Frontend: `getRemoteProjects()` in `PlatformService`
- [x] Frontend: Project config component fully rewritten — new import flow replacing old manual project form
- [x] Frontend: Import panel with provider dropdown, candidate list with checkboxes, per-candidate editing, select/deselect all, import progress
- [x] Frontend: 15 new import flow tests replacing old project form tests
- [x] Frontend: SCSS budget in `angular.json` increased from 8kB to 12kB error limit (component legitimately large)
- [x] All 710 Angular tests passing, Angular build passing

### Bug Fix: Frontend ApiResponse Unwrapping & AuthService Mock (Gateway fetch failures)
- [x] Root cause 1: `PlatformService.getConnections()` called `GET /api/platforms/connections` — **no such backend endpoint** (only `GET /api/platforms/connections/tenant/{tenantId}` exists). Fixed to accept `tenantId` param and call correct URL.
- [x] Root cause 2: `getConnection()`, `createConnection()`, `updateConnection()`, `testConnection()` all expected raw payloads but backend wraps everything in `ApiResponse<T>`. Added `.pipe(map(r => r.data))` unwrapping.
- [x] Root cause 3: `testConnection()` had wrong return type `{ success: boolean; message: string }` — backend returns `ApiResponse<boolean>`. Changed to `Observable<boolean>`.
- [x] Fix: `PlatformConnectionsComponent` updated to inject `AuthService` and pass `user.tenantId` to `getConnections()`.
- [x] Test fixes: `platform.service.spec.ts` — all 5 methods now flush `ApiResponse`-wrapped data; `getConnections` test uses tenant URL.
- [x] Test fixes: `platform-connections.component.spec.ts` — added `AuthService` mock, `getConnections` assertion checks `tenantId`.
- [x] Test fixes: `agent-config.component.spec.ts` — added missing `AuthService` mock (pre-existing bug, `tenantId` was `''` instead of `'demo-tenant-001'`).
- [x] Test fixes: `notification-preferences.component.spec.ts` — added missing `AuthService` mock (pre-existing bug, `userId` was `''` instead of `'demo-user-001'`).
- [x] All 392 backend platform tests passing, all 710 Angular tests passing (0 failures)

### Fix: WebSocket 401 Authentication Gap
- [x] Root cause: Three-part gap — frontend sent no JWT on WebSocket connect, gateway requires auth on `/ws/**`, no query-param token extraction filter existed
- [x] Gateway: Created `WebSocketTokenFilter` (WebFilter, order -2) that extracts JWT from `?access_token=` query param on WebSocket upgrade and injects `Authorization: Bearer` header
- [x] Frontend: `notification.service.ts` — inject `AuthService`, append `?access_token=<jwt>` to WS URL
- [x] Frontend: `websocket.service.ts` — inject `AuthService`, append `?access_token=<jwt>` to WS URL
- [x] Both services handle null token gracefully (no param appended)
- [x] Tests: 12 backend `WebSocketTokenFilterTest` unit tests + 2 notification + 2 websocket frontend tests
- [x] All backend tests passing, all 756 Angular tests passing

### Feature 13: User Platform Tokens UI
- [x] Frontend: `user-token.model.ts` — `UserPlatformToken`, `PatLinkRequest`, `OAuth2LinkRequest`, `ConnectionInfo`, `OAuth2AuthorizeUrl` interfaces
- [x] Frontend: `UserTokenService` — 7 methods: `getTokensByUser`, `linkPat`, `linkOAuth2`, `linkGeneric`, `unlinkAccount`, `getAvailableConnections`, `getOAuth2AuthorizeUrl`
- [x] Frontend: `UserTokensComponent` — standalone component (145 lines TS, 110 lines HTML, 259 lines SCSS) with linked accounts list and link form
- [x] Frontend: Integrated as 6th tab `platform-tokens` in unified settings page
- [x] Tests: 10 `UserTokenService` tests + 17 `UserTokensComponent` tests
- [x] All 756 Angular tests passing

### Feature 14: Project Config Label & Card Enhancements
- [x] Labels generalized from ticket-specific ("Ticket Providers") to platform-inclusive ("Providers")
- [x] 8 label changes: subtitle, tab name, form title, empty states, import dropdown, name/URL placeholders
- [x] Project card headers enhanced with platform type badge (`getConnectionPlatformType()`)
- [x] Connection status indicator with color variants: active, connected, disconnected, error, inactive (`getConnectionStatus()`)
- [x] Improved mapping label with `getMappingLabel()` helper
- [x] SCSS: Added `__type-badge` and `__status` variant styles
- [x] Tests: 7 new tests for helper methods
- [x] All 756 Angular tests passing

### Feature 15: SSH Key Management + 4-Step Setup Wizard
- [x] Backend: `SshKey` entity — `id UUID`, `tenant_id`, `connection_id` (FK to platform_connections with CASCADE delete), `name`, `public_key`, `private_key` (encrypted via TokenEncryptionService), `fingerprint` (SHA-256), `key_type` (ED25519/RSA)
- [x] Backend: `CreateSshKeyRequest` DTO with Jakarta validation (`@NotBlank name`, `@NotNull connectionId`, `@NotBlank publicKey`, `@NotBlank privateKey`, optional keyType defaulting to ED25519)
- [x] Backend: `SshKeyResponse` DTO — safe response without private key, includes `fromEntity()` mapper
- [x] Backend: `SshKeyRepository` — findByConnectionId, findByTenantId, findByConnectionIdAndFingerprint
- [x] Backend: `SshKeyService` — full CRUD with encryption, SHA-256 fingerprint computation, duplicate detection
- [x] Backend: `SshKeyController` at `/api/platforms/ssh-keys` — GET (by connection, by tenant), POST, DELETE
- [x] Backend: `platform_category VARCHAR(50)` column on `platform_connections` table — values: `TICKET_PROVIDER` or `GIT_REMOTE`
- [x] Backend: `PlatformConnectionService.determinePlatformCategory()` — GITHUB/GITLAB/BITBUCKET → GIT_REMOTE, others → TICKET_PROVIDER
- [x] Backend: `PlatformConnectionService.listConnectionsByTenantAndCategory()` + auto-set in `createConnection()`
- [x] Backend: `GET /api/platforms/connections/tenant/{tenantId}/category/{category}` endpoint
- [x] Backend: `ConnectionInfoResponse` updated with `platformCategory` field
- [x] Backend: Flyway V6 migration (`V6__add_ssh_keys_and_platform_category.sql`) — creates `ssh_keys` table, adds `platform_category` column with backfill
- [x] Backend: `branchNamingTemplate VARCHAR(500)` column on `projects` table (default: `{strategy}/{ticket}-{description}`)
- [x] Backend: `Project` entity and `CreateProjectRequest` DTO updated with `branchNamingTemplate`
- [x] Backend: `ProjectService` handles `branchNamingTemplate` in create/update
- [x] Backend: Flyway V4 migration (`V4__add_branch_naming_template.sql`) for orchestrator
- [x] Backend: 474 platform tests passing (27 SshKeyService + 14 SshKeyController + 5 SshKeyResponse + 8 CreateSshKeyRequest + 13 PlatformConnectionService + 3 PlatformConnectionController + existing)
- [x] Backend: 328 orchestrator tests passing (+4 ProjectService + fixed CreateProjectRequest)
- [x] Frontend: `PlatformCategory` enum, `SshKey`, `CreateSshKeyRequest` interfaces in `security.model.ts`
- [x] Frontend: `BranchStrategyType` enum, `branchNamingTemplate` on `Project`, `BITBUCKET` on `PlatformType` in `project.model.ts`
- [x] Frontend: `SshKeyService` — full CRUD service extending `ApiService` (8 tests)
- [x] Frontend: `PlatformService.getConnectionsByCategory()` method (+2 tests)
- [x] Frontend: Project config rewritten as 4-step setup wizard (1040-line component, 813-line template):
  - Step 1: **Ticket Providers** — Add/manage JIRA Cloud/Server, Azure DevOps connections (TICKET_PROVIDER category)
  - Step 2: **Git Remotes** — Add/manage GitHub/GitLab/Bitbucket connections (GIT_REMOTE category) + SSH key CRUD (generate/delete per connection)
  - Step 3: **Projects** — Import projects from configured providers, set git clone URL, default branch, description
  - Step 4: **Branch & Workflow** — Branch naming template per project with live preview, workflow state mappings, connection status indicators
- [x] Frontend: Settings component updated with 7th wizard-style tab integration
- [x] Frontend: 81 project-config component tests covering all wizard steps, SSH key CRUD, categorization, branch naming, import flow, workflow mappings
- [x] Frontend: 798 Angular tests passing (42 net new tests)

### Feature 16: SSH Key Integration for Git Operations (Agent Checkout with SSH Keys)
- [x] Backend (squadron-workspace): `WorkspaceGitService` rewritten with SSH support
  - `isSshUrl()` detects `git@` and `ssh://` URL prefixes
  - `setupSshKey()` writes SSH private key to `/tmp/.squadron_ssh_key` with `chmod 600`
  - `cleanupSshKey()` removes temporary key file in `finally` block
  - `GIT_SSH_COMMAND` env var: `ssh -i /tmp/.squadron_ssh_key -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null`
  - 3-arg `cloneRepository(workspaceId, accessToken, sshPrivateKey)` — SSH for SSH URLs, HTTPS token for HTTPS URLs
  - 4-arg `pushChanges(workspaceId, branch, accessToken, sshPrivateKey)` — same dual-mode logic
  - Original 2-arg/3-arg overloads delegate to new methods with `null` sshPrivateKey (backward compatible)
- [x] Backend (squadron-workspace): `CreateWorkspaceRequest` — added `sshPrivateKey` field
- [x] Backend (squadron-workspace): `WorkspaceService` — passes `request.getSshPrivateKey()` to git clone
- [x] Backend (squadron-workspace): `WorkspaceController` — added `sshKeyId` optional param to clone/push endpoints, `resolveSshPrivateKey()` helper fetches key via Feign
- [x] Backend (squadron-workspace): `PlatformServiceClient` — new Feign client interface for `GET /api/platforms/ssh-keys/{id}/private-key`
- [x] Backend (squadron-workspace): `ResilientPlatformServiceClient` — circuit breaker + retry wrapper
- [x] Backend (squadron-workspace): `SquadronWorkspaceApplication` — added `@EnableFeignClients`
- [x] Backend (squadron-workspace): `pom.xml` — added `spring-cloud-starter-openfeign` dependency
- [x] Backend (squadron-workspace): `application.yml` — added `squadron.platform.url` config
- [x] Backend (squadron-platform): `SshKeyController` — added `GET /{id}/private-key` endpoint returning `ApiResponse<String>` with decrypted key
- [x] Tests: 12 new `WorkspaceGitServiceTest` tests (SSH URL detection, SSH clone/push success, cleanup on failure, HTTPS ignores SSH key, delegation overloads)
- [x] Tests: 3 new `WorkspaceServiceTest` tests (SSH key passthrough, both token+key passthrough, graceful failure)
- [x] Tests: 5 new `WorkspaceControllerTest` tests (clone/push with/without sshKeyId, combined accessToken+sshKeyId)
- [x] Tests: 5 new `ResilientPlatformServiceClientTest` tests (delegation, retry, circuit breaker open, CB exception, accessor)
- [x] Tests: 2 new `SshKeyControllerTest` tests (get private key authenticated, get private key unauthenticated)
- [x] Tests: 2 pre-existing tests updated (WorkspaceServiceTest auto-clone stubs changed from 2-arg to 3-arg)
- [x] Tests: SecurityConfigTest updated (added `@MockBean ResilientPlatformServiceClient`)
- [x] All 191 workspace tests passing, all 476 platform tests passing

### Feature 17: Agent Listener Refactor (Unified TaskStateDispatcher)
- [x] Created `TaskStateDispatcher.java` — single unified NATS listener replacing 5 individual per-state listeners
- [x] Handles all states (PLANNING, CODING, REVIEW, QA, MERGE) via switch statement dispatching to appropriate service
- [x] Deleted: PlanningAgentListener, CodingAgentListener, ReviewAgentListener, QAAgentListener, MergeListener (and their tests)
- [x] PlanApprovalListener kept separate (different NATS subject: `squadron.agent.plan.approved`)
- [x] EventFlowValidationTest rewritten to use TaskStateDispatcher
- [x] Tests: 20 test methods in TaskStateDispatcherTest (all states + error handling + unknown states)
- [x] All squadron-agent tests passing

### Feature 18: i18n Support (English + French)
- [x] Frontend: `@ngx-translate/core@17` + `@ngx-translate/http-loader@17` with `provideTranslateHttpLoader()` (v17 API)
- [x] Frontend: `I18nService` — init, switchLanguage, currentLang signal, localStorage persistence, browser language detection, backend persistence
- [x] Frontend: English (`en.json`) and French (`fr.json`) comprehensive translation files in `src/assets/i18n/`
- [x] Frontend: Language switcher dropdown in header (left of user avatar) and login page (top right)
- [x] Frontend: Sidebar nav items use `labelKey` with translate pipe
- [x] Frontend: Dashboard StatCard uses `labelKey` with translate pipe
- [x] Frontend: Settings tabs use `labelKey` with translate pipe
- [x] Frontend: All 6 affected component specs updated for TranslateModule.forRoot() and key-based assertions
- [x] Backend: `GET /api/users/{userId}/preferences` and `PATCH /api/users/{userId}/preferences` endpoints on squadron-identity
- [x] Backend: `UserService.getUserPreferences()` and `updateUserPreferences()` with JSONB merge-style update of `settings` field
- [x] Backend: 4 new UserController tests + 6 new UserService tests
- [x] Gateway: `user-service` route added for `/api/users/**` (17 routes total)
- [x] Gateway: GatewayConfigTest updated (24 tests)
- [x] Angular build passing, all backend tests passing

### Feature 19: Verbose Notification Event Types
- [x] Frontend: `eventTypes` changed from `string[]` to objects with `{ type, labelKey, descriptionKey }`
- [x] Frontend: HTML template shows translated labels and descriptions
- [x] Frontend: SCSS with `__event-info`, `__event-label`, `__event-description` classes
- [x] Frontend: Spec updated for new event type structure

### Feature 20: Mock Data Removal (18 Components)
- [x] Removed mock/demo data from all 18 components that silently populated UI with fake data on API error
- [x] All error handlers now set empty arrays `[]` or `null` instead of calling getMock*/applyMockData methods
- [x] Removed methods: getMockTasks, getMockReviews, getMockUsers, getMockTeams, getMockGroups, getMockPermissions, getMockConnections, getMockProviders, getMockStatuses, applyMockData, applyMockSettings
- [x] Components affected: project-list, project-detail, task-board, task-detail, dashboard, agent-chat, review-list, review-detail, qa-report, settings, user-management, team-management, security-group-management, permission-management, platform-connections, auth-provider-config, usage-dashboard, project-config
- [x] All 18 corresponding spec files updated (mock data assertions → empty state assertions)
- [x] Removed unused `PercentPipe` import from agent-chat component
- [x] Angular build passing, all backend tests passing

### Infrastructure Improvements (This Session)
- [x] Rootless containers: nginx-unprivileged for UI, rootless Redis and Mailpit
- [x] Hibernate dialect removal from 18 application.yml/application-integration.yml files (Spring Boot auto-detection)
- [x] Surefire argLine fix: `-XX:+EnableDynamicAgentLoading -Xshare:off` for Java 21 + Mockito compatibility
- [x] All 20 containers healthy with testldap-build-and-start.sh

### Feature 21: Jira Server Test Instance
- [x] Docker: `atlassian/jira-software:9.12-jdk17` added to `docker-compose-testldap.yml` with shared PostgreSQL, memory limits (1536M), and healthcheck
- [x] Database: `jira` database added to `init-databases.sql` (auto-created on first startup)
- [x] Flyway V7: `V7__seed_testldap_jira_connection.sql` seeds `JIRA_SERVER` platform connection for Planet Express tenant with placeholder PAT
- [x] Scripts: `testldap-build-and-start.sh` updated with Jira in infra services, console setup instructions, LDAP integration guide
- [x] Scripts: `testldap-stop.sh` updated with Jira references
- [x] Backend: `RepositoryIntegrationTest` fixed to filter by tenantId (V7 seed adds extra row)
- [x] Port mapping: host 8090 → container 8080 (Tomcat default), healthcheck uses internal port 8080
- [x] Healthcheck matches `RUNNING`, `FIRST_RUN`, and `ERROR` states (ERROR = setup wizard not yet completed)
- [x] All 20 containers healthy (Jira starts in background, 2-5 min first boot)

### Bug Fix: Jira Database Not Created on Existing PostgreSQL Volumes
- [x] Root cause: `init-databases.sql` only runs once when the PostgreSQL volume is first initialized; if the `jira` database was added after the volume already existed, it was never created
- [x] Fix: Added `ensure_databases()` function to `testldap-build-and-start.sh` that runs after PostgreSQL is healthy but before starting dependent services
- [x] The function checks each required database via `SELECT 1 FROM pg_database` and creates any missing ones with `CREATE DATABASE ... OWNER squadron` + `pgcrypto` extension
- [x] Handles both fresh installs (init script creates DBs) and existing volumes (ensure_databases fills gaps)

### Feature 22: GitLab CE Test Instance
- [x] Docker: `gitlab/gitlab-ce:17.4.0-ce.0` added to `docker-compose-testldap.yml` with bundled PostgreSQL (self-contained)
- [x] LDAP: Pre-configured via `GITLAB_OMNIBUS_CONFIG` to use Planet Express test directory (`openldap-test:10389`)
- [x] Performance tuning: Puma workers=2, Sidekiq concurrency=5, Prometheus/Grafana/Registry/Pages disabled
- [x] Ports: HTTP `8929:80`, SSH `2424:22`
- [x] Volumes: `gitlab-config`, `gitlab-logs`, `gitlab-data`
- [x] Memory limits: 4096M (limit), 2048M (reservation), 256m shm_size
- [x] Healthcheck: `/-/readiness?all=1` endpoint, 15s interval, 40 retries, 180s start_period
- [x] Flyway V8: `V8__seed_testldap_gitlab_connection.sql` seeds `GITLAB` platform connection for Planet Express tenant with `platform_category = 'GIT_REMOTE'`, `auth_type = 'PAT'`, placeholder PAT
- [x] Connection ID: `c0000000-0000-0000-0000-000000000002`, base_url: `http://gitlab-ce:80`
- [x] Scripts: `testldap-build-and-start.sh` updated with GitLab in `INFRA_SERVICES` and `SLOW_HEALTHCHECK_SERVICES`, console setup instructions (root password retrieval, PAT creation, LDAP login)
- [x] Scripts: `testldap-stop.sh` updated with GitLab references
- [x] All 3,818 backend tests passing (V8 migration has no impact on existing tests)

### Feature 23: Agent Test (Test from My Agent Squadron UI)
- [x] Backend: Flyway V5 migration `V5__create_agent_test_config.sql` — `agent_test_configs` table for per-user test generator config
- [x] Backend: `AgentTestConfig` entity, `AgentTestConfigRepository`, `AgentTestConfigDto`
- [x] Backend: `AgentTestRequest` DTO (agentConfigId + testMode), `AgentTestResult` DTO (status, summary, logEntries, agentOutput, durationMs)
- [x] Backend: `AgentTestConfigService` — CRUD for test generator configuration
- [x] Backend: `TestDataGeneratorService` — generates fake plans/code/reviews via configurable LLM with fallback stubs
- [x] Backend: `AgentTestExecutionService` — orchestrates full test: resolve config → generate data → invoke agent → collect results with verbose logs
- [x] Backend: `AgentTestController` at `/api/agents/test` with POST `/execute`, GET `/config`, PUT `/config`
- [x] Backend: Gateway `agent-test` route added before `agent-squadron` in GatewayConfig (19 routes total)
- [x] Backend: 45 unit tests across 7 test files (controller, services, DTOs); 986 squadron-agent tests passing
- [x] Frontend: `agent-test.model.ts` — TestMode, AgentTestRequest, AgentTestResult, TestLogEntry, AgentTestConfig interfaces
- [x] Frontend: `agent-test.service.ts` — executeTest, getTestConfig, updateTestConfig
- [x] Frontend: `SquadronConfigComponent` rewrite — Test button on agent cards, test mode selector menu, expandable/collapsible test result panel with verbose log entries, agent output viewer, spinner, dismiss
- [x] Frontend: `TestGeneratorConfigComponent` — new Settings tab for test data generator config (provider/model/hosting type using PROVIDER_CATALOG)
- [x] Frontend: Settings component updated with 7th tab "Test Generator" (`test-generator`)
- [x] Frontend: i18n keys added to en.json and fr.json for test generator and squadron config test features
- [x] Frontend: `squadron-config.component.spec.ts` updated with 10 new test cases for test functionality
- [x] Frontend: `test-generator-config.component.spec.ts` — 15 tests
- [x] Frontend: `agent-test.service.spec.ts` — 10 tests
- [x] Frontend: `settings.component.spec.ts` updated for 7th tab
- [x] Angular build passing
- [x] Bug fix: Default agents changed to 2 local agents — Sol (Gemma 4) + Titan (Qwen 2.5 Coder), both self-hosted via Ollama
- [x] Bug fix: Reset squadron — added `repository.flush()` after delete to prevent unique constraint violation when re-seeding same agent names
- [x] Bug fix: OllamaProvider — removed `@ConditionalOnBean(OllamaChatModel.class)`, now always registered with nullable auto-configured model; creates dynamic `OllamaApi` + `OllamaChatModel` on-the-fly from agent config's baseUrl when auto-configured model unavailable; models cached by base URL
- [x] Bug fix: Added `baseUrl` and `hostingType` fields to `AgentConfigDto`; `AgentTestExecutionService` now passes baseUrl/hostingType through when building agent and generator configs, ensuring local Ollama agents are invoked correctly instead of falling back to OpenAI

### Feature 24: Ticketless Tasks
- [x] Backend (orchestrator): Flyway V8 migration `V8__add_ticketless_task_support.sql` — adds ticketless columns (ticketless, ticketless_status, branch_name, create_branch, agent_mode, agent_config_id, prompt), makes project_id nullable, adds partial index
- [x] Backend (orchestrator): `TicketlessStatus` enum (CREATED, PLANNING, BUILDING, COMPLETED, FAILED)
- [x] Backend (orchestrator): `CreateTicketlessTaskRequest` DTO with Jakarta validation
- [x] Backend (orchestrator): `Task` entity updated with new fields, `@Builder.Default` for boolean fields
- [x] Backend (orchestrator): `TaskRepository.findByTenantIdAndTicketlessTrue()`
- [x] Backend (orchestrator): `TaskService` — `createTicketlessTask()`, `getTicketlessTasks()`, `updateTicketlessStatus()`, `getTasksByState()` excludes ticketless tasks
- [x] Backend (orchestrator): `TaskController` — `POST /ticketless`, `GET /ticketless`, `PUT /{id}/ticketless-status`
- [x] Backend (orchestrator): 8 new TaskServiceTest + 4 new TaskControllerTest, all 411 orchestrator tests passing
- [x] Backend (common): `TicketlessTaskCreatedEvent` NATS event class
- [x] Backend (agent): `OrchestratorClient` + `ResilientOrchestratorClient` — `updateTicketlessStatus()` Feign method
- [x] Backend (agent): `TicketlessTaskService` — resolves agent config, invokes AgentService.chat(), updates status
- [x] Backend (agent): `TicketlessTaskListener` — NATS listener on `squadron.tasks.ticketless.created`
- [x] Backend (agent): 8 TicketlessTaskServiceTest + 5 TicketlessTaskListenerTest, all passing
- [x] Frontend: `task.model.ts` — `TicketlessStatus` enum, `CreateTicketlessTaskRequest`, `TicketlessTask` interface
- [x] Frontend: `task.service.ts` — `createTicketlessTask()`, `getTicketlessTasks()`, `updateTicketlessStatus()`
- [x] Frontend: `task-board.component.ts` — ticketless column, create dialog, agent selection, mode selection
- [x] Frontend: `task-board.component.html` — "New Ticketless Task" button, ticketless column with status/mode/branch display, create dialog modal
- [x] Frontend: `task-board.component.scss` — ticketless column styling, status colors, branch badge, checkbox label
- [x] Frontend: i18n keys in en.json and fr.json for all ticketless UI strings
- [x] All 4,328 backend tests passing (0 failures, 0 errors across 11 modules)
- [x] Angular production build passing

---

## Phase 9: Credential Delegation & End-to-End Agent Git Workflow (Epics 1-11)

### Epic 1: Credential Resolution Service (squadron-platform + squadron-common)
- [x] `CredentialResolutionService` — single entry point tries strategies in order: OAuth2 → PAT → Deploy Key → GitHub App → Fail
- [x] `CredentialResolutionResult` DTO (common) — carries resolved token, auth mode, credential type
- [x] `ResolveCredentialRequest` DTO (common) — userId, connectionId, purpose
- [x] `CredentialPurpose` enum (common) — GIT_CLONE, GIT_PUSH, API_CALL, REVIEW_BOT
- [x] `CredentialType` enum (common) — OAUTH2, PAT, SSH_KEY, GITHUB_APP
- [x] `GitAuthMode` enum (common) — HTTPS_TOKEN, SSH_KEY, GITHUB_APP
- [x] `CredentialController` REST endpoint at `/api/platforms/credentials/resolve`
- [x] Audit logging in resolution service (auditCredentialResolved / auditCredentialResolutionFailed)
- [x] Tests for all DTOs, enums, service, and controller

### Epic 2: GitHub App Token Service (squadron-platform)
- [x] `GitHubAppTokenService` — JWT generation from app private key + installation token exchange
- [x] `getInstallationToken(UUID connectionId)` — generates short-lived GitHub App installation token
- [x] Integrated into CredentialResolutionService fallback chain
- [x] Tests for token generation and integration

### Epic 3: SSH Key Usage & Deploy Keys (squadron-platform)
- [x] `key_usage` column on `ssh_keys` table (USER_KEY / DEPLOY_KEY)
- [x] `SshKey` entity updated with `keyUsage` field
- [x] `SshKeyRepository.findByConnectionIdAndKeyUsage()` query
- [x] Flyway V9 migration: `V9__add_ssh_key_usage.sql`
- [x] Deploy key support in credential resolution chain
- [x] Tests for repository, service, and migration

### Epic 4: Review Bot Configuration (squadron-review)
- [x] `ReviewBotConfig` entity — per-project bot configuration (enabled, bot user, permissions)
- [x] `ReviewBotConfigService` — CRUD + `getEnabledBotConfig()` returning Optional
- [x] `ReviewBotConfigController` at `/api/reviews/bot-configs`
- [x] Flyway V3 migration: `V3__create_review_bot_configs.sql`
- [x] Tests for entity, service, and controller

### Epic 5: Workspace Lifecycle Service (squadron-agent)
- [x] `WorkspaceLifecycleService` — orchestrates workspace creation, credential injection, branch setup
- [x] `WorkspaceInfo` DTO — workspace ID, container ID, branch, clone URL
- [x] Integrates with CredentialClient for token resolution and WorkspaceServiceClient for container ops
- [x] Auto-clone with resolved credentials, branch creation via git checkout -b
- [x] Tests for lifecycle management

### Epic 6: Agent Credential Clients (squadron-agent)
- [x] `CredentialServiceClient` (Feign) — calls squadron-platform credential resolution endpoint
- [x] `ResilientCredentialServiceClient` — circuit breaker + retry wrapper
- [x] `CredentialClient` tool — resolveCredentials(userId, connectionId, purpose)
- [x] `ReviewBotClient` tool — getEnabledBotConfig() returning Optional<BotConfig>
- [x] application.yml updated with credential service URL
- [x] Tests for all clients

### Epic 7: Git Module Credential Integration (squadron-git)
- [x] `CredentialServiceClient` + `ResilientCredentialServiceClient` (Feign) in squadron-git
- [x] `CreatePullRequestRequest` updated with userId + connectionId for credential resolution
- [x] `MergeRequest` updated with userId + connectionId
- [x] `PullRequestService` resolves credentials before calling git platform adapters
- [x] `GitCliService` uses resolved tokens for push/pull operations
- [x] `@EnableFeignClients` on SquadronGitApplication
- [x] pom.xml: added spring-cloud-starter-openfeign dependency
- [x] application.yml: added platform service URL
- [x] Tests for updated DTOs, services, and credential flow

### Epic 8: Orchestrator Task Context Enrichment
- [x] `TaskContext` DTO (common) — carries userId, connectionId, projectId through NATS events
- [x] `TaskStateChangedEvent` updated with TaskContext field
- [x] `WorkflowEngine` enriches TaskContext when publishing state change events
- [x] Agent services (Coding, Review, QA, Merge) extract context for credential resolution
- [x] Tests for enriched events and context propagation

### Epic 9: Git Credential Security Hardening
- [x] Token sanitization in `GitCliService` (squadron-git) — strips tokens from git remote URLs and output
- [x] Token sanitization in `WorkspaceGitService` (squadron-workspace) — consistent regex pattern
- [x] Token sanitization in `WorkspaceClient` (squadron-agent) — sanitizes exec stdout/stderr
- [x] Token sanitization in `CodingAgentService` (squadron-agent) — sanitizes tool results
- [x] Single comprehensive regex: `(https?://)[^@/]+@` → `$1***@` for all 4 modules
- [x] Credential TTL already handled by GitHub App short-lived tokens and OAuth2 refresh
- [x] Audit logging already in CredentialResolutionService (Epic 1)
- [x] 18 new security tests across 4 modules
- [x] `WebSocketIntegrationTest` updated with 6 new `@MockBean` entries for Epic 1-8 beans
- [x] `WorkspaceRepositoryTest` fixed: method count assertion 5→6 for `findByStatus()` addition

### Epic 10: Frontend Updates
- [x] GitHub App connection type: added `installationId` field to App auth type in project config
- [x] Deploy key UI: key usage toggle (USER_KEY/DEPLOY_KEY), generate deploy key button, public key display
- [x] Review bot configuration section: enable/disable per-project, bot user/token fields, CRUD operations
- [x] `ReviewBotConfigService` — new Angular service with CRUD methods
- [x] Credential status indicators on project cards (ACTIVE/EXPIRED/MISSING color variants)
- [x] `SshKeyService.generateDeployKey()` method
- [x] `security.model.ts` updated with KeyUsage enum, ReviewBotConfig, CredentialStatus
- [x] ~30 new project-config tests + 8 review-bot-config service tests + 1 ssh-key service test

### Epic 11: Integration Testing & Validation
- [x] `PatCredentialFlowIntegrationTest` (squadron-agent) — 4 tests: PAT resolution, workspace provisioning, git push, PR creation
- [x] `OAuth2CredentialFlowIntegrationTest` (squadron-agent) — 4 tests: OAuth2 resolution, token refresh, workspace with OAuth2, push with refreshed token
- [x] `ReviewBotFlowIntegrationTest` (squadron-agent) — 3 tests: bot-enabled review submission, bot-disabled fallback, credential resolution for bot posting
- [x] `MultiAgentIsolationIntegrationTest` (squadron-agent) — 2 tests: parallel workspaces isolated, separate branch/credential per agent
- [x] `CredentialFallbackChainIntegrationTest` (squadron-platform) — 5 tests: OAuth2 first, PAT fallback, SSH key for git ops, GitHub App for GitHub connections, clear error when no credentials

### Frontend Test Fixes (12 tests fixed)
- [x] `DiffViewerComponent should_show_empty_state` — i18n key mismatch (expected literal text, template uses translate pipe)
- [x] `NotificationPreferencesComponent should show webhook URL fields when Slack enabled` — i18n key mismatch
- [x] `PlatformConnectionsComponent should_updateLastSyncAt_when_syncConnectionFails` — incorrect assertion (component only resets syncingId on failure)
- [x] `ProjectConfigComponent should_passKeyUsage_whenSavingSshKey` — expected DEPLOY_KEY but form value is USER_KEY
- [x] `ReviewDetailComponent should render tabs` / `should show diff viewer in diff tab` — test setup used error state causing null review
- [x] `ReviewListComponent should render review table rows` — @empty block renders 1 row, not 0
- [x] `SettingsComponent should handle save error with optimistic success` — component missing optimistic success in error handler
- [x] `SquadronConfigComponent` (3 tests) — i18n key mismatches
- [x] `UsageDashboardComponent should call service on init` — missing AuthService injection for tenantId
- [x] All 841 Angular tests now passing (0 failures)

### Phase 10: UX Polish & Provider Editing

#### Translation Key Audit (168 missing keys fixed)
- [x] Audited all Angular templates and TS files for translation keys missing from i18n JSON
- [x] Added 168 missing flat-alias keys across 13 sections in en.json and fr.json
- [x] Sections fixed: agentChat, diffViewer, qaReport, reviews, projects, tasks, settings.agentConfig, settings.userTokens, settings.squadronConfig, projectConfig.reviewBot, projectConfig.sshKeys, projectConfig.credentialStatus, projectConfig.authFields
- [x] Both JSON files validated as valid JSON

#### Celestial Body Agent Names
- [x] Renamed 8 default AI agent names from whimsical to celestial body names
- [x] Backend: `UserAgentConfigService.java` DEFAULT_AGENTS updated (Sol, Titan, Vega, Comet, Pulsar, Quasar, Nova, Nebula)
- [x] Backend: `UserAgentConfigServiceTest` + `UserAgentConfigControllerTest` assertions updated
- [x] Frontend: `squadron-config.component.spec.ts` + `user-squadron.service.spec.ts` updated
- [x] All backend + frontend tests passing

#### Provider Editing (Edit Ticket Providers & Git Remotes)
- [x] `getFirstAuthType()` private helper — resolves stored authType strings to AUTH_TYPE_OPTIONS labels
- [x] `editTicketProvider(conn)` — populates form with existing connection data, credentials left empty
- [x] `editGitRemote(conn)` — same pattern for git remotes
- [x] `saveTicketProvider()` — detects edit mode, calls `updateConnection()` instead of `createConnectionFromRequest()`
- [x] `saveGitRemote()` — same edit-mode detection for git remotes
- [x] `canSaveTicketProvider()` / `canSaveGitRemote()` — credentials optional when editing
- [x] Credentials only sent in update if user fills them in (empty = keep existing)
- [x] HTML template: Edit buttons on ticket provider and git remote cards
- [x] HTML template: Form title shows "Edit" vs "New" based on editing state
- [x] HTML template: Save button shows "Update" vs "Save" in edit mode
- [x] HTML template: Credential placeholders show "Leave blank to keep existing" in edit mode
- [x] Translation keys added to en.json and fr.json (editTitle, updateProvider/updateRemote, credentialEditHint, editSuccess, card.edit)
- [x] 17 new tests for edit functionality (edit form population, credential handling, update API calls, error handling, allConnections sync)
- [x] All 858 Angular tests passing

#### Task Board Redesign (3-Column Work Board)
- [x] Collapsed 6 Kanban columns into 3 swimlane columns: In Progress (IN_PROGRESS + REVIEW + QA), Planned (BACKLOG + PLANNING), Completed (DONE)
- [x] `BoardTask` interface extending `Task` with `projectName`, `agentStatus`, `agentType` fields
- [x] `BoardColumn` interface with `id`, `label`, `color`, `icon`, `states`, `tasks` fields
- [x] Project enrichment: tasks display project name via `ProjectService.getProjectsByTenant()` with map lookup
- [x] Agent status indicators: colored badges (ACTIVE green, WAITING_INPUT amber, COMPLETED indigo, FAILED red)
- [x] Summary bar: total counts for In Progress, Planned, Completed with color-coded dots
- [x] Project filter dropdown: filter tasks by project across all columns
- [x] Cancel task flow: interrupt agent (`AgentService.interruptAgent`) → transition to BACKLOG → cleanup workspace (`WorkspaceService.destroyWorkspace`)
- [x] Quick prompt: inline text input to send messages to active agent sessions (`AgentService.sendMessage`)
- [x] Plan approval: approve/reject agent plans from the board inline (`AgentService.approvePlan/rejectPlan`)
- [x] Open Agent Chat: navigate to `/agent/:taskId` from action panel
- [x] Task cards rendered inline with priority badge, external ID, project label, agent status, state badge, labels
- [x] Drag-drop preserved between 3 swimlane columns (CDK DragDrop with `transferArrayItem` and revert on error)
- [x] Created `WorkspaceService` (Angular) — `getWorkspaceByTask(taskId)` and `destroyWorkspace(workspaceId)` methods
- [x] Created `workspace.service.spec.ts` — 6 tests (create, GET, DELETE, error handling)
- [x] Translation keys added: `tasks.board.summary.*`, `tasks.board.agentStatus.*`, `tasks.board.actions.*`, `tasks.board.filter.allProjects`, `tasks.board.column.planned/completed`, `tasks.board.loading`, `tasks.board.refresh`
- [x] Rewrote `task-board.component.spec.ts` — 44 tests covering 3-column structure, project enrichment, summary counts, filtering (priority, project, search, combined), drag-drop, cancel flow, prompt sending, plan approval, agent status, navigation, toggle actions, refresh
- [x] All 899 Angular tests passing (was 858; +41 net new tests)

#### Bug Fix: `/api/tasks/by-state` 500 Error
- [x] Root cause: `TaskController.getTasksByState()` and `getTaskStats()` required `@RequestParam UUID tenantId` but frontend called without it
- [x] Fix: Both endpoints now use `TenantContext.getTenantId()` (populated by `TenantFilter` from `X-Tenant-Id` header set by gateway)
- [x] Removed `@RequestParam` from both methods, added `TenantContext` import
- [x] `TaskControllerTest` updated: 4 tests now use `TenantContext.setContext()` + `@AfterEach` cleanup instead of `.param("tenantId", ...)`
- [x] All orchestrator tests passing, all 899 Angular tests passing

### Phase 11: Agent Squadron Overhaul (Per-Agent Provider/Model Configuration)

#### Backend Changes
- [x] Flyway V4 migration: `V4__add_agent_config_fields.sql` — adds `hosting_type VARCHAR(50) DEFAULT 'PLATFORM'`, `base_url VARCHAR(500)`, `api_key_ref TEXT`, `description VARCHAR(500)` to `user_agent_configs`
- [x] `UserAgentConfig.java` entity updated with `hostingType` (default "PLATFORM"), `baseUrl`, `apiKeyRef`, `description` fields with `@Builder.Default`
- [x] `UserAgentConfigDto.java` updated with matching fields, `@Size` validation, `@Builder.Default` for hostingType
- [x] `UserAgentConfigService.java` fully rewritten:
  - DEFAULT_AGENTS now 5-element arrays: `{name, type, provider, model, description}`
  - `seedDefaults()` sets provider, model, hostingType, description for each default agent
  - `addAgent()` and `updateAgent()` handle all new fields
  - `generateDescription(provider, model, hostingType)` auto-generates display descriptions
  - `humanizeProvider()` and `humanizeModel()` for display-friendly names
- [x] `UserAgentConfigServiceTest.java` rewritten — 24 tests (seed defaults verify provider/model/hostingType/description, auto-description generation for platform/self-hosted/custom/null)
- [x] `UserAgentConfigControllerTest.java` rewritten — 10 tests with new fields in buildAgent helper and JSON path assertions
- [x] All 932 squadron-agent tests passing

#### Frontend Model Changes
- [x] `squadron-config.model.ts` fully rewritten:
  - `HostingType` type: `'PLATFORM' | 'SELF_HOSTED' | 'CUSTOM'`
  - `ProviderCatalogEntry` and `ModelCatalogEntry` interfaces
  - `PROVIDER_CATALOG` constant with 6 providers (GitHub Copilot, Anthropic, OpenAI, Ollama, Cohere, Google) and their model lists
  - `generateAgentDescription()` function matching backend logic
  - `UserAgentConfig` interface extended with `hostingType`, `baseUrl`, `apiKeyRef`, `description`

#### Frontend Component Changes
- [x] `squadron-config.component.ts` fully rewritten with rich inline template:
  - Agent cards: name, description, hosting type badge (Cloud=blue/Local=green/Custom=purple)
  - Edit form: hosting type selector → provider dropdown (filtered by type) → model catalog dropdown → base URL (SELF_HOSTED/CUSTOM) → API key (CUSTOM) → maxTokens/temperature → description → system prompt → enabled
  - `onHostingTypeChange()` clears provider/model/baseUrl/apiKeyRef, re-filters providers
  - `onProviderChange()` clears model, updates available models
  - `autoDescription()` returns generated description for placeholder
  - `filteredProviders` and `availableModels` signals for reactive UI
- [x] `squadron-config.component.spec.ts` rewritten — 31 tests (cards, badges, editing, hosting type/provider changes, save with new fields, auto-description, error handling, add/remove/reset, filtered providers, self-hosted badge, `generateAgentDescription` standalone tests)

#### i18n Changes
- [x] `en.json` updated: `hostingType`, `hostingTypes.platform/selfHosted/custom`, `selectProvider`, `selectModel`, `customProvider`, `baseUrl`, `apiKey`, `apiKeyPlaceholder`, `description`, `descriptionHint`, `systemPrompt`, `systemPromptPlaceholder`
- [x] `fr.json` updated: French translations for all same keys
- [x] Agent Config subtitle updated in both languages

#### Verification
- [x] All 915 Angular tests passing (0 failures)
- [x] All backend tests passing across all 11 modules (~4,063 tests, BUILD SUCCESS)
- [x] All 20 Docker containers healthy after rebuild with testldap-build-and-start.sh

---

### Phase 12: Review Bot Integration (End-to-End Bot Comment Posting)

#### squadron-common
- [x] Added `REVIEW_BOT` value to `CredentialPurpose` enum (5th value, after FULL)
- [x] `CredentialPurposeTest` updated: count 4→5, ordinal test, valueOf test, REVIEW_BOT-specific test
- [x] All 64 squadron-common tests passing

#### squadron-git
- [x] `PullRequestService.addReviewComment(UUID recordId, String body, String accessToken)` — resolves PR record, calls adapter's `addReviewComment` with bot token
- [x] `PullRequestController` — added `POST /{id}/review-comment` endpoint with `@RequestBody String body` and `@RequestParam accessToken`
- [x] `PullRequestServiceTest` — 2 new tests (success + not found)
- [x] `PullRequestControllerTest` — 1 new test (`should_addReviewComment`), uses `TEXT_PLAIN` content type
- [x] All 371 squadron-git tests passing

#### squadron-agent
- [x] `GitClient.addPrReviewComment(UUID prId, String body, String accessToken)` — calls git service to post review comment
- [x] `GitClient.requestPrReviewers(UUID prId, List<String> reviewers, String accessToken)` — assigns reviewers
- [x] `ReviewAgentService` — added `ReviewBotClient` + `GitClient` constructor params
- [x] `ReviewAgentService.postReviewBotComments(TaskContext, List<ReviewFinding>)` — extracts connectionId, fetches bot config & token, finds PR, formats comment, posts via GitClient, optionally auto-assigns bot as reviewer
- [x] `ReviewAgentService.formatBotReviewComment(List<ReviewFinding>)` — markdown table with severity/file/description per finding
- [x] Bot comment posting is **non-fatal** — failures logged as warnings, don't fail the review
- [x] `GitClientTest` — 4 new tests (addPrReviewComment success/failure, requestPrReviewers success/failure)
- [x] `ReviewAgentServiceTest` — 8 new tests (6 bot posting scenarios + 2 format tests)
- [x] `WebSocketIntegrationTest` — fixed pre-existing context failure by adding `squadron.platform.service-url` property
- [x] All 944 squadron-agent tests passing (948 total, 4 WebSocket tests still have pre-existing SockJS 500 issue)

#### squadron-platform
- [x] `CredentialResolutionService.isGitPurpose()` — added comment clarifying `REVIEW_BOT` is intentionally excluded from credential resolution chain
- [x] `CredentialResolutionServiceTest` — 1 new test (`should_returnFalse_when_purposeIsReviewBot`)
- [x] All 511 squadron-platform tests passing

#### Build & Test Verification
- [x] `mvn compile` passes across all modules
- [x] Identified and documented pre-existing Lombok stale cache issue: `mvn clean compile` required for Lombok annotation processing; stale `target/` classes from previous non-Lombok builds break downstream modules. Fix: always use `mvn clean` prefix.
- [x] All 1,890 tests passing across 4 changed modules (64 + 371 + 944 + 511)

---

### Phase 13: Task Sync from Ticket Providers (End-to-End Fetch & Board UI)

#### Backend Bug Fixes (squadron-orchestrator)
- [x] `TaskSyncService` URL fix: changed from `GET /api/platform-sync/{connectionId}/tasks` to `POST /api/platforms/sync/{connectionId}/tasks` (matching `PlatformSyncController`)
- [x] `PlatformServiceClient` Feign interface: `@GetMapping` → `@PostMapping`, URL path corrected to match
- [x] `TaskSyncService` workflow initialization: added `WorkflowEngine` dependency, calls `initializeWorkflow(tenantId, taskId, null)` for newly created tasks so they appear on the task board in BACKLOG state
- [x] `ProjectControllerTest` fix: `should_return403_when_developerTriesToDelete` used `developer` role but `@PreAuthorize` already permits it; changed to `viewer` role
- [x] `TaskSyncServiceTest` updated: mocks changed from `get()` to `post()`, `WorkflowEngine` mock added, task IDs set on save mocks, workflow verification in create/multi-sync tests
- [x] `PlatformServiceClientTest` updated: `@GetMapping` → `@PostMapping` assertion, URL path assertion updated

#### Frontend: Task Sync UI (squadron-ui)
- [x] `task.model.ts`: Added `TaskSyncRequest` interface (tenantId, teamId, projectId, platformConnectionId, projectKey) and `TaskSyncResult` interface (created, updated, unchanged, failed, errors)
- [x] `task.service.ts`: Added `syncTasks(request: TaskSyncRequest): Observable<TaskSyncResult>` method with `ApiResponse` unwrapping
- [x] `task.service.spec.ts`: Added `should_syncTasks_when_calledWithSyncRequest` test
- [x] `task-board.component.ts`: Added sync signals (`showSyncPanel`, `selectedSyncProjectId`, `syncing`, `syncResult`, `syncError`), `syncableProjects` computed signal (filters projects with both `connectionId` AND `externalProjectId`), `toggleSyncPanel()`, `closeSyncPanel()`, `resetSyncState()`, `syncTasks()` methods
- [x] `task-board.component.html`: "Sync Tasks" button (visible only when syncable projects exist), sync panel with project dropdown, start/syncing button, result summary (created/updated/unchanged/failed counts), error display, close button
- [x] `task-board.component.scss`: Styles for `__sync-panel`, `__sync-header`, `__sync-description`, `__sync-form`, `__sync-result`, `__sync-result-summary`, `__sync-errors`, `__sync-error`
- [x] `task-board.component.spec.ts`: 9 new sync tests — syncableProjects filtering, empty syncable projects, toggle/close panel with state reset, syncTasks service call, error handling, no-op when no project selected, auto-reload after sync with changes, no reload when no changes
- [x] `en.json` + `fr.json`: Added `tasks.board.sync.*` translation keys (button, title, description, selectProject, syncing, start, resultCreated, resultUpdated, resultUnchanged, resultFailed, error)

#### Build & Test Verification
- [x] `mvn clean verify` — all 12 modules SUCCESS, 0 failures
- [x] `ng build --configuration=production` — Angular build passing
- [x] All 915 Angular tests passing (0 failures)

### Phase 14: Live Testing Bug Fixes

#### Bug 1: 403 on POST /api/reviews/bot-config for developer role
- [x] Root cause: `ReviewBotConfigController` `@PreAuthorize` on POST, PUT, DELETE, and GET token endpoints only allowed `squadron-admin` and `team-lead` roles — missing `developer`
- [x] Fix: Added `'developer'` to `@PreAuthorize` on all 4 restricted endpoints
- [x] All 245 squadron-review tests passing
- [x] Docker image rebuilt, container recreated and healthy

#### Bug 2: nginx healthcheck fails (IPv6 connection refused)
- [x] Root cause: `nginx.conf` only had `listen 8080` (IPv4). BusyBox `wget` in Alpine resolves `localhost` to `::1` (IPv6) first, gets connection refused. The compose healthcheck uses `127.0.0.1` (correct), but manual `docker run` used `localhost`.
- [x] Fix: Added `listen [::]:8080;` to `nginx.conf` for defense-in-depth (works with both IPv4 and IPv6 healthchecks)
- [x] Container recreated with correct `127.0.0.1` healthcheck, now healthy

#### Bug 3: "Not configured" label on collapsed project cards (Branch & Workflow tab)
- [x] Root cause: `getMappingLabel()` in `project-config.component.ts` checked `ps.expanded` instead of `ps.mappings.length`. After expanding a card (which loads mappings from server) and collapsing it, the badge reverted to "Not configured" despite mappings being in memory.
- [x] Fix: Changed condition from `ps.expanded` to `ps.mappings.length > 0`
- [x] Tests updated: renamed existing test, added `should_getMappingLabel_returnMappingCount_when_collapsedWithMappings` test
- [x] All 926 Angular tests passing (924 success, 2 pre-existing ProjectService failures unrelated to this change)
- [x] Docker image rebuilt, container recreated and healthy

### Phase 15: Live Testing Bug Fixes Round 2

#### Bug 4: 403 on POST /api/tasks/sync for developer role
- [x] Root cause: `TaskController.syncTasks()` `@PreAuthorize` only allowed `squadron-admin` and `team-lead` roles — missing `developer`
- [x] Fix: Added `'developer'` to `@PreAuthorize` on sync endpoint
- [x] Tests: 3 new tests (`should_syncTasks_when_developerRole`, `should_syncTasks_when_teamLeadRole`, `should_return403_when_viewerTriesToSync`)
- [x] All orchestrator tests passing

#### Bug 5: 401 on WebSocket /ws/notifications
- [x] Root cause: Gateway `SecurityConfig.java` did not include `/ws/**` in `permitAll()` path matchers — all WebSocket paths fell under `.anyExchange().authenticated()`
- [x] Fix: Added `"/ws/**"` to the `permitAll()` path matchers in gateway SecurityConfig
- [x] WebSocketTokenFilter (order -200) handles JWT extraction from `access_token` query param; downstream notification service handles STOMP auth
- [x] Tests: 1 new gateway SecurityConfigTest verification test
- [x] All gateway tests passing

#### Bug 6: "Not configured" badge on all projects when one fails (Branch & Workflow step)
- [x] Root cause: Eager mapping fetch used `forkJoin` for all projects' mapping requests; if ANY one failed, the entire forkJoin errored and no mappings populated
- [x] Fix: Added `catchError(() => of([] as WorkflowMapping[]))` to each individual project mapping request, so one failure doesn't block others
- [x] Removed forkJoin-level error handler (individual errors now caught)

#### Bug 7: UI not refreshing after "Save Mappings" click
- [x] Root cause: `saveMappings()` `updateProject()` call only sent `branchNamingTemplate` and `defaultBranch`, missing `connectionId` and `externalProjectId`
- [x] Fix: Added `connectionId` and `externalProjectId` to `updateProject()` payload; preserved `mappingsLoaded: true` in success handler
- [x] Tests: Updated `should_saveMappings` test expectations

#### Cleanup: Removed default branch from step 3 (Projects)
- [x] Default branch display was moved to step 4 (Branch & Workflow) — removed duplicate `<span>` from step 3 project cards
- [x] Removed default branch input field from import candidate details in step 3

#### Custom Spring Boot Banners
- [x] Created `banner.txt` in `src/main/resources/` for all 10 service modules
- [x] Each banner displays Squadron ASCII art logo + spaced-out service name (e.g., `I D E N T I T Y   S E R V I C E`)
- [x] Includes Spring Boot version and Java version placeholders
- [x] Modules: identity, gateway, orchestrator, platform, agent, workspace, git, review, notification, config

#### Build & Test Verification
- [x] `mvn clean verify` — all modules pass except 4 pre-existing WebSocketIntegrationTest failures in squadron-agent
- [x] All other ~4,059 backend tests passing (0 failures)
- [x] Angular tests: 926 passing (2 pre-existing ProjectService failures)

#### Bug 8: Task Sync I/O Error in Docker (PLATFORM_SERVICE_URL not set)
- [x] Root cause: `TaskSyncService` uses a `RestClient` with base URL from `${PLATFORM_SERVICE_URL:http://localhost:8084}`. In Docker, `localhost` points to the orchestrator container itself — the platform service is at `squadron-platform:8084` on the Docker network. The env var was never set in `docker-compose.yml`.
- [x] Fix: Added `PLATFORM_SERVICE_URL: http://squadron-platform:8084` to `x-common-env` anchor in `docker-compose.yml` (used by orchestrator, git, and agent)
- [x] Also added missing inter-service URLs to squadron-agent: `ORCHESTRATOR_URL`, `GIT_SERVICE_URL`, `REVIEW_SERVICE_URL`, `WORKSPACE_SERVICE_URL`
- [x] Also added missing `SQUADRON_PLATFORM_URL` to squadron-workspace
- [x] Docker compose config validated, all 20 containers healthy after rebuild

#### Bug 9: Task Sync 401 Unauthorized (JWT not forwarded to platform service)
- [x] Root cause: After fixing the I/O error, the orchestrator's `RestClient` could reach the platform service but got 401 because it wasn't forwarding the JWT. The platform service's `SecurityConfig` requires authentication for `/api/platforms/sync/**`.
- [x] Fix: Added `extractBearerToken()` method to `TaskSyncService` that reads the JWT from `SecurityContextHolder` and adds an `Authorization: Bearer` header to the `RestClient` call
- [x] Tests: Updated `TaskSyncServiceTest` with lenient mock for the `header()` call since unit tests don't have a SecurityContext

#### Bug 10: Jira JQL double URL-encoding (400 Bad Request on /rest/api/2/search)
- [x] Root cause: `JiraServerAdapter.fetchTasks()` (and `JiraCloudAdapter`) manually encoded the JQL with `URLEncoder.encode()`, then passed the result to `webClient.get().uri(string)` which re-encoded it. This produced garbled JQL that Jira rejected with 400.
- [x] Fix: Replaced manual `URLEncoder.encode()` + string URI with `uriBuilder` using `queryParam()` — WebClient handles encoding correctly via `UriBuilder`
- [x] Removed unused `java.net.URLEncoder` import from both adapters
- [x] Tests: Updated `setupGetMock()` in both `JiraServerAdapterTest` and `JiraCloudAdapterTest` with lenient `Function`-based URI mock alongside string-based URI mock
- [x] All 530 platform tests passing

#### Systemic Fix: FeignConfig JWT Forwarding (RequestInterceptor)
- [x] Root cause: `FeignConfig` in `squadron-common` had no `RequestInterceptor` — all Feign-based inter-service calls from authenticated user requests would silently drop the JWT, causing 401 on downstream services
- [x] Fix: Added `authorizationForwardingInterceptor()` bean that reads `Authorization: Bearer` header from the incoming HTTP request via `RequestContextHolder` and forwards it to all outbound Feign calls
- [x] Gracefully skips forwarding when no HTTP request context (NATS-triggered, scheduled calls)
- [x] Tests: 5 new `FeignConfigTest` tests (interceptor creation, bearer forwarding, no auth header, non-bearer auth, no request context)
- [x] All 606 common tests passing

#### Bug 11: Update Connection tenantId Validation Error (400 on PUT /api/platforms/connections/{id})
- [x] Root cause: `PUT /api/platforms/connections/{id}` reused `CreateConnectionRequest` DTO which has `@NotNull tenantId`. The frontend correctly doesn't send `tenantId` on updates (tenant can't change), so Jakarta validation failed with "tenantId: Tenant ID is required".
- [x] Fix: Created dedicated `UpdateConnectionRequest` DTO without `tenantId` field — only includes mutable fields (`name`, `platformType`, `baseUrl`, `authType`, `credentials`, `metadata`)
- [x] `PlatformConnectionController.updateConnection()` now accepts `UpdateConnectionRequest` instead of `CreateConnectionRequest`
- [x] `PlatformConnectionService.updateConnection()` rewritten with true partial update logic — only non-null fields applied to existing entity
- [x] Tests: `UpdateConnectionRequestTest` (6 tests), controller and service tests updated for new DTO
- [x] All 536 platform tests passing

#### Build & Test Verification (Phase 15 final)
- [x] `mvn clean verify` — all modules pass except 4 pre-existing WebSocketIntegrationTest failures in squadron-agent
- [x] All other ~4,068 backend tests passing (0 failures)
- [x] Angular tests: 926 passing (2 pre-existing ProjectService failures)
- [x] Task sync fully working end-to-end with live Jira Server instance
- [x] All 20 Docker containers healthy

### Phase 16: Live Testing Bug Fixes Round 3

#### BUG 12: authType sends i18n key instead of backend value
- [x] **Root cause:** `AUTH_TYPE_OPTIONS` in `project-config.component.ts` used `label` (i18n translation key like `'projectConfig.authTypes.pat'`) as both the display label AND the value sent to the backend. The backend stores plain strings like `"PAT"`, `"API Token"`, etc.
- [x] **Fix:** Added a `value` field to each `AUTH_TYPE_OPTIONS` entry with the backend value, changed all form lookups/assignments from `label` to `value`, updated `getTicketAuthTypeOptions()` and `getGitAuthTypeOptions()` return types to include `value`, updated `getFirstAuthType()` to match/return `value` with legacy i18n key fallback, updated `newTicketForm()`/`newGitForm()` defaults, updated HTML template `<option>` elements to use `[value]="opt.value"` while displaying `{{ opt.label | translate }}`
- [x] **Files:** `project-config.component.ts`, `project-config.component.html`, `project-config.component.spec.ts`

#### BUG 13: NotificationService path mismatches
- [x] **Root cause:** Three frontend service methods used wrong URLs/methods vs backend controller
- [x] **Fix 1:** `getNotifications()` — changed from `GET /notifications` to `GET /notifications/user/${userId}`
- [x] **Fix 2:** `markAsRead()` — changed from `POST /notifications/{id}/read` to `PUT /notifications/{id}/read`
- [x] **Fix 3:** `markAllAsRead()` — changed from `POST /notifications/read-all` to `PUT /notifications/user/${userId}/read-all`
- [x] **Files:** `notification.service.ts`, `notification.service.spec.ts` (all 13+ tests updated)

#### Build & Test Verification (Phase 16 final)
- [x] Angular tests: 932 passing (0 failures)
- [x] Backend: no changes, pre-existing squadron-identity Lombok annotation-processing test failures unrelated

#### BUG 14: Jira Cloud returns 0 tasks on sync
- [x] **Root cause:** The Jira Cloud API user (`Paul.Lessard@cyber.gc.ca`) has **no project access**. Diagnostic `GET /rest/api/3/project` returns 0 visible projects. The API token is valid (auth succeeds with 200), but the user lacks "Browse Projects" permission in Jira Cloud for PRTK or any project. This is a Jira Cloud admin configuration issue, not a code bug.
- [x] **Debug deployed:** Added diagnostic calls in `fetchTasks()` — listed visible projects (0), tried no-project-filter JQL (400), tried quoted project key (empty). Confirmed: permissions issue.
- [x] **Improvement:** Added post-fetch diagnostic in `JiraCloudAdapter.fetchTasks()` — when 0 tasks found, calls `getProjects()` to check visibility and logs actionable warnings (no visible projects, project not visible, or project visible but empty).
- [x] **Cleanup:** Removed all temporary debug logging from `JiraCloudAdapter.java`.
- [x] **Fix:** `TicketingPlatformAdapterTest` method count updated 9→10 (was off by 1 since `normalizeBaseUrl` default method was added).
- [x] **DB fix:** Updated stored `base_url` from `cccs.atlassian.net` to `https://cccs.atlassian.net` in `platform_connections` table.
- [x] All 550 platform tests passing (0 failures)
- [x] Docker image rebuilt, container recreated and healthy

#### Test Connection buttons + Import dropdown fix
- [x] **Test Connection signals/method** added to `project-config.component.ts`: `testingConnectionId` and `testConnectionResult` signals, `testConnection(conn)` method calling `platformService.testConnection()`, updates connection status using `ConnectionStatus` enum
- [x] **Test Connection buttons** in HTML template: Step 1 (Ticket Providers) and Step 2 (Git Remotes) each show a Test button per provider/remote card with spinner, success/error badge, auto-clear after 5 seconds
- [x] **Import dropdown fixed** (Step 3): changed from `allConnections()` to `ticketProviders()` for button guard, empty state, dropdown `@for`, and `toggleImportPanel()` auto-select — since projects are imported from ticket providers only, not git remotes
- [x] **SCSS styling** added for `.provider-card__test-result` with `--success` and `--error` variants
- [x] **Translation keys** added to both `en.json` and `fr.json`: `projectConfig.testConnection.test`, `testing`, `success`, `failed`
- [x] **13 new tests** in `project-config.component.spec.ts`: 7 test connection tests (success, failure, network error, auto-clear with fakeAsync, testing state, update all lists, import dropdown filter) + fixed `should_notAutoSelect_when_multipleProviders` test
- [x] All 939 Angular tests passing

#### BUG 15: repositoryUrl ↔ repoUrl field mismatch (silent data loss on project import)
- [x] **Root cause:** Frontend `Project` interface uses `repositoryUrl` field but backend `CreateProjectRequest` DTO and `Project` entity use `repoUrl`. Jackson silently ignores unrecognized `repositoryUrl` during deserialization, so the clone URL is always `null` after import or update.
- [x] **Fix (CreateProjectRequest):** Added `@JsonProperty("repositoryUrl")` and `@JsonAlias("repoUrl")` to the `repoUrl` field — accepts both field names on input, serializes as `repositoryUrl`
- [x] **Fix (Project entity):** Added `@JsonProperty("repositoryUrl")` to the `repoUrl` field — serializes as `repositoryUrl` in API responses so frontend can read it correctly
- [x] **Tests:** 3 new tests in `CreateProjectRequestTest` (deserialize `repositoryUrl`, deserialize `repoUrl` alias, serialize as `repositoryUrl`) + 1 new test in `ProjectTest` (serialize `repoUrl` as `repositoryUrl`)
- [x] All 348 orchestrator tests passing
- [x] Orchestrator + UI Docker images rebuilt, containers recreated and healthy

### Phase 17: Tasks/Projects UI Revamp, Backend API Enhancements & Test Coverage

#### Backend DTOs & Endpoints
- [x] **ProjectSummaryDto** — new DTO with project id, name, totalTasks, doneTasks, activeTasks, taskStates map
- [x] **TaskDetailDto** — new DTO with full task detail including workflow state, available transitions, project context
- [x] **DelegateTaskRequest** — new DTO with agentType, agentName, instructions, targetState + Jakarta @NotBlank validation
- [x] **ProjectController** — `GET /api/projects/summaries?tenantId=` endpoint (DEVELOPER role)
- [x] **TaskController** — `GET /api/tasks/{taskId}/detail` and `POST /api/tasks/{taskId}/delegate` endpoints (DEVELOPER role)
- [x] **ProjectService** — `getProjectSummaries(tenantId)` method computing task counts per project
- [x] **TaskService** — `getTaskDetail(taskId)` and `delegateToAgent(taskId, request)` methods

#### Frontend Services & Models
- [x] **TaskService** — `getTaskDetail()` and `delegateToAgent()` methods
- [x] **ProjectService** — `getProjectSummaries()` and `getProjectsByTenant()` methods
- [x] **task.model.ts** — TaskDetail, DelegateTaskRequest interfaces; all 9 TaskState enum values (BACKLOG, PRIORITIZED, PLANNING, PROPOSE_CODE, IN_PROGRESS, REVIEW, QA, MERGE, DONE)
- [x] **project.model.ts** — ProjectSummary interface

#### Task Board Component Revamp
- [x] 3-column board (planned/in-progress/completed) with correct state mapping for all 9 states
- [x] Planned column: BACKLOG + PRIORITIZED + PLANNING
- [x] In-progress column: PROPOSE_CODE + IN_PROGRESS + REVIEW + QA + MERGE
- [x] Completed column: DONE
- [x] Delegation panel: toggleDelegatePanel(), delegateTask() with 4 agent types (PLANNING, CODING, REVIEW, QA)
- [x] List view mode toggle (board/list) with allTasks computed
- [x] State filter (filterState signal) combining with search/priority/project filters
- [x] stateColor() returning distinct color for all 9 states
- [x] totalTasks, totalPlanned, totalInProgress, totalCompleted computed signals
- [x] Task sync panel with project selection and result display
- [x] Drag-drop between columns with transition and revert on failure

#### Project List Component Revamp
- [x] Project summaries with task count aggregates
- [x] Search filtering (name/description/externalProjectId)
- [x] Sync projects with connection + project key validation
- [x] View mode toggle and navigation

#### WebSocket Integration Test Fix
- [x] **Root cause:** WebSocketIntegrationTest connected to `/ws/agent/websocket` but the endpoint is registered WITHOUT SockJS fallback, so the correct raw WebSocket URL is `/ws/agent`
- [x] **Fix:** Changed URL from `ws://localhost:{port}/ws/agent/websocket` to `ws://localhost:{port}/ws/agent`
- [x] All 4 WebSocket integration tests now passing

#### Test Coverage Added
- [x] **Backend DTO tests:** ProjectSummaryDtoTest (11), TaskDetailDtoTest (11), DelegateTaskRequestTest (14) = 36 new tests
- [x] **Backend service tests:** ProjectServiceTest +3, TaskServiceTest +7 = 10 new tests
- [x] **Backend controller tests:** ProjectControllerTest +2, TaskControllerTest +4 = 6 new tests
- [x] **Frontend service tests:** task.service.spec +2, project.service.spec +2 = 4 new tests
- [x] **Frontend project-list spec:** Fully rewritten — 32 tests
- [x] **Frontend task-board spec:** 365 new lines added — delegation panel, list view, state filter, all 9 states, stateColor, totalTasks, allStates/agentTypes constants, toggleTaskActions delegate panel interaction = ~30 new tests
- [x] **Total:** ~118 new tests across backend and frontend
- [x] All 4,215 backend tests passing (0 failures, 0 errors)

### Phase 18: Reviews UI Revamp

#### Frontend Models
- [x] **review.model.ts** — Extended from 61 to 114 lines: added `ReviewSummary`, `QAReport`, `QAVerdict`, `ReviewOrchestrationResult`, `SubmitReviewRequest` interfaces/enums, added `summary?` and `reviewerId?` fields to `Review`

#### Frontend Services
- [x] **review.service.ts** — Extended from 37 to 107 lines: added 9 new methods (`getReviewsForTask`, `deleteReview`, `submitReview`, `checkReviewGate`, `orchestrateReview`, `checkAndTransition`, `getQAReports`, `getLatestQAReport`, `checkQAGate`). New methods using `ApiResponse<T>` wrapper call `this.http.get/post` directly with `.pipe(map(r => r.data))`
- [x] **review.service.spec.ts** — Extended from 9 to 28 tests: added tests for all 9 new methods including `checkReviewGate` with/without teamId, `orchestrateReview` with/without teamId, `checkAndTransition` with/without teamId, `getQAReports`, `getLatestQAReport`, `checkQAGate` true/false

#### Review List Component
- [x] **review-list.component.ts** — Revamped from 46 to 171 lines: signals for `filterStatus`, `filterReviewerType`, `searchQuery`, `sortField`, `sortDirection`, `error`; computed signals: `filteredReviews` (client-side search + reviewer type filter + sort), `totalReviews`, `pendingCount`, `inProgressCount`, `approvedCount`, `changesRequestedCount`, `rejectedCount`, `totalLinesChanged`, `totalUnresolved`; methods: `openReview()`, `refresh()`, `onFilterStatusChange()`, `toggleSort()`, `statusColor()`, `unresolvedCount()`
- [x] **review-list.component.html** — Revamped from 54 to 123 lines: summary stats bar, filters row (search + status + reviewer type), sortable columns (status, changes, updated), unresolved comment count column
- [x] **review-list.component.scss** — Updated with stats bar, filters, sort icons, unresolved badge styles
- [x] **review-list.component.spec.ts** — Fully rewritten: 42 tests covering creation, loading/error, filtered reviews (search by title/repo/PR number, reviewer type filter), summary counts (totalReviews, pendingCount, inProgressCount, approvedCount, changesRequestedCount, rejectedCount), totalLinesChanged, totalUnresolved, sorting (toggleSort all fields/directions), statusClass for all 5 statuses, statusColor for all 5, unresolvedCount, onFilterStatusChange reloads, openReview navigation, refresh, allStatuses/allReviewerTypes, DOM rendering

#### Review Detail Component
- [x] **review-detail.component.ts** — Revamped from 82 to 272 lines: signals for `error`, `reviewGate`, `gateLoading`, `qaReport`, `qaGatePassed`, `qaLoading`, `requestChangesText`, `requestingChanges`, `orchestrating`, `orchestrationResult`, `resolvingCommentId`, `approving`, `rejecting`; computed: `unresolvedCount`, `resolvedCount`, `totalComments`, `canAct`; methods: `loadReviewGate()`, `loadQAReport()`, `requestChanges()`, `resolveComment()`, `triggerReReview()`, `verdictClass()`, `statusClass()`, `coveragePercent()`; 3 tabs: diff, comments, qa
- [x] **review-detail.component.html** — Revamped from 89 to 197 lines: review gate indicator, orchestration result alert, QA tab (verdict, coverage, tests, summary), request changes form, resolve comment buttons, re-review trigger button, status badges
- [x] **review-detail.component.scss** — Updated with review gate, QA section, request changes form, verdict badge styles
- [x] **review-detail.component.spec.ts** — Fully rewritten: 56 tests covering creation, loading/error, loadReview + checkDiff + loadReviewGate + loadQAReport, approve (success/error/no review), reject (success/error/no review), requestChanges (success/error/empty text/no review), resolveComment (success/error/no review), triggerReReview (success/error/no review), tabs (diff/comments/qa), canAct for all 5 statuses, unresolvedCount/resolvedCount/totalComments, verdictClass for all 3 verdicts, severityClass for 4 severities, statusClass for 5 statuses, coveragePercent edge cases, DOM rendering (diff viewer, 3 tabs, comments, action buttons, gate, orchestration result, QA section, request changes form, resolve/resolved badges)

#### Translation Keys
- [x] **en.json** — Added 32 new keys: `reviews.list.refresh`, `searchPlaceholder`, `allReviewers`, `comments`, `stats.total/linesChanged/unresolved`; `reviews.detail.requestChanges`, `requestChangesPlaceholder`, `resolve`, `reReview`, `qaTab`, `qaReport`, `qaVerdict`, `qaSummary`, `noQAReport`, `qaGatePassed`, `qaGateFailed`, `lineCoverage`, `branchCoverage`, `testsPassed`, `testsFailed`, `testsSkipped`, `gate.label/met/unmet/humanApprovals/aiApproval/yes/no`, `orchestrationResult`, `reviewsCreated`, `pendingHuman`; severity keys with uppercase aliases (CRITICAL, MAJOR, MINOR, INFO)
- [x] **fr.json** — Corresponding French translations for all 32 new keys

#### Template Fixes
- [x] **review-detail.component.html** — Fixed `@else if (qaReport(); as qa)` (Angular `@else if` doesn't support `as` alias) — replaced all `qa.` references with `qaReport()!.`
- [x] **review-list.component.ts** — Fixed `statusColor()` signature to accept `ReviewStatus | string` (template passes string literals from translate pipe context)

#### Build & Test Verification
- [x] Angular build passing (production configuration)
- [x] `mvn clean verify` — all 4,215 backend tests passing (0 failures, 0 errors)

### Phase 19: Translation Fixes, Git Access Rewrite, Gemma 4 Support

#### Translation Fixes (6 issues fixed)
- [x] **task-board.component.html** — Raw `task.priority` enum (e.g., "CRITICAL") displayed in board view (line 166) and list view (line 272) → Changed to `{{ 'tasks.priority.' + task.priority.toLowerCase() | translate }}`
- [x] **task-card.component.ts** — Raw `task().priority` enum displayed without translation, `TranslateModule` missing from imports → Added `TranslateModule` to component imports, applied translate pipe
- [x] **task-detail.component.html** — `tasks.detail.tokens` key displayed without interpolation param → Changed to `{{ 'tasks.detail.tokens' | translate:{ count: t.tokenUsage | number } }}`
- [x] **review-detail.component.html** — Raw `comment.category` value (e.g., "SECURITY") → Changed to `{{ 'reviews.detail.category.' + comment.category | translate }}`
- [x] **review-detail.component.html** — Raw `comment.authorType` value (e.g., "AI") → Changed to `{{ 'reviews.reviewerType.' + comment.authorType | translate }}`
- [x] **en.json + fr.json** — Added 9 `reviews.detail.category.*` translation keys (SECURITY, PERFORMANCE, MAINTAINABILITY, RELIABILITY, STYLE, BUG, BEST_PRACTICE, DOCUMENTATION, OTHER)

#### Git Access Test Rewrite (ProcessBuilder → Container-based)
- [x] **Root cause:** Previous session replaced container-based `testGitAccess()` with `ProcessBuilder`-based `git ls-remote` running directly on JVM host. This fails because `git` is not installed in the workspace service's JVM container (`Cannot run program "git": Exec failed, error: 2 (No such file or directory)`).
- [x] **Fix:** Reverted to container-based approach using `workspaceProvider.createContainer()` to spin up ephemeral ubuntu:22.04 container, `ensureGitInstalled()`, run `git ls-remote` inside it via `workspaceProvider.exec()`, then `workspaceProvider.destroyContainer()` in `finally` block.
- [x] HTTPS support: token injected into URL via `injectTokenIntoUrl()` before passing to `exec()`
- [x] SSH support: uses existing `setupSshKey(containerId, sshPrivateKey)` to write key inside container, `execWithSshCommand()` for GIT_SSH_COMMAND wrapping, `cleanupSshKey(containerId)` in inner `finally` block
- [x] Branch detection from `ls-remote` output (parsed from `ExecResult.getStdout()`)
- [x] Container always destroyed in outer `finally` block (even on error)
- [x] Removed obsolete `createProcessBuilder()` and `writeTemporarySshKey()` methods
- [x] Removed unused imports (`List`, `Map`, `Path`, `Files`, `ProcessBuilder`, `BufferedReader`, `InputStreamReader`, `PosixFilePermission`, `Set`, `ArrayList`, `TimeUnit`)
- [x] **WorkspaceGitServiceTest** — 8 old ProcessBuilder/spy-based tests replaced with 8 new container-based mock tests using `workspaceProvider` mock directly (no spy needed)
- [x] New test: `should_testGitAccess_destroyContainerEvenWhenCreateFails` — verifies no `destroyContainer()` call when `createContainer()` throws

#### Gemma 4 Model Support
- [x] **squadron-config.model.ts** — Added Gemma 4 to Google provider (`gemma-4` / "Gemma 4") and Ollama provider (`gemma4` / "Gemma 4") in `PROVIDER_CATALOG`
- [x] **UserAgentConfigService.java** — Added `case "gemma-4", "gemma4" -> "Gemma 4"` to `humanizeModel()` switch
- [x] **UserAgentConfigServiceTest** — 2 new tests for Gemma 4 description generation (Google platform + Ollama local)

#### Pre-existing Test Fixes
- [x] **task-board.component.spec.ts** — Fixed `delegateToAgent.and.returnValue(of({} as Task))` → `of(void 0)` (method returns `Observable<void>`, not `Observable<Task>`) — 3 occurrences
- [x] **task-board.component.spec.ts** — Added default `agentServiceSpy.getSession` mock return value (was undefined, caused `TypeError` in `loadAgentSessions()`)
- [x] **task-card.component.spec.ts** — Added `TranslateModule.forRoot()` to test imports

#### Build & Test Verification
- [x] `mvn verify` — all 11 modules BUILD SUCCESS, 4,218 tests passing (0 failures, 0 errors)
- [x] All 1,123 Angular tests passing (0 failures)

#### Task Display Bug Fix (Tasks not showing after sync)
- [x] **Root cause:** `TaskService.getTasksByState()` and `getTaskStats()` in `task.service.ts` called `this.get<T>(url)` which returns the raw HTTP response body. The backend wraps responses in `ApiResponse<T>` (`{ success, data, message, timestamp }`), so the service returned the wrapper object instead of the inner `data`. When `buildColumns()` accessed `tasksByState['BACKLOG']`, it got `undefined` because the top-level keys are `success`, `data`, `timestamp` — all columns defaulted to `[]`.
- [x] **Fix:** Both methods now use `this.http.get<ApiResponse<T>>(url).pipe(map(r => r.data))` to unwrap the `ApiResponse.data` field
- [x] **Tests:** Updated `should_getTasksByState_when_called` and `should_getTaskStats_when_called` in `task.service.spec.ts` to flush `ApiResponse`-wrapped payloads

### Phase 20: Runtime Bug Fixes (Translation Keys "undefined" + Git Access NPE)

#### BUG: "Test Git Access" NPE — DockerWorkspaceProvider null-safe labels
- [x] **Root cause:** `WorkspaceGitService.testGitAccess()` creates a `WorkspaceSpec` with only `baseImage("ubuntu:22.04")` — `taskId` and `tenantId` are null. `DockerWorkspaceProvider.createContainer()` called `spec.getTaskId().toString()` and `spec.getTenantId().toString()` to build Docker container labels, causing NPE. The exception was caught by the generic catch block and wrapped as `RuntimeException("Failed to create Docker workspace")`.
- [x] **Fix:** Made label construction in `DockerWorkspaceProvider.createContainer()` null-safe using a `HashMap` — only adds `squadron.task-id` and `squadron.tenant-id` labels when values are non-null. The `squadron.app` label is always set.
- [x] **Files:** `DockerWorkspaceProvider.java` (lines 140-151, added `import java.util.HashMap`), `DockerWorkspaceProviderTest.java` (new test)
- [x] **Test:** `should_createContainer_withNullTaskIdAndTenantId()` verifies no NPE and correct label map (only `squadron.app`, no task-id/tenant-id)
- [x] All 21 DockerWorkspaceProviderTest tests passing

#### BUG: Translation keys showing "undefined" — 17 locations fixed
- [x] **Root cause 1 — Agent Chat connection state case mismatch:** `agent-chat.component.html` uses `connectionState()` which returns lowercase values (`connected`, `connecting`, `disconnected`, `error`) but translation keys in en.json were UPPERCASE only (`agentChat.connection.CONNECTED`, etc.)
- [x] **Fix 1:** Added lowercase key aliases (`connected`, `connecting`, `disconnected`) alongside existing uppercase keys, plus new `error` key in both en.json and fr.json
- [x] **Root cause 2 — `.priority.toLowerCase()` on null:** 4 template locations called `.toLowerCase()` on `task.priority` which can be `undefined` from API responses
- [x] **Fix 2:** Added null guards: `task.priority ? ('tasks.priority.' + task.priority.toLowerCase() | translate) : ''` in task-detail, task-board (board + list views), and task-card templates
- [x] **Root cause 3 — Missing `TESTING` category translation:** `ReviewCategory` enum includes `TESTING` but `reviews.detail.category.TESTING` was not in i18n files
- [x] **Fix 3:** Added `reviews.detail.category.TESTING` to en.json ("Testing") and fr.json ("Test")
- [x] **Root cause 4 — `tasks.state.undefined`:** Task state can be undefined/null from API responses
- [x] **Fix 4:** Added null guard on state display in task-detail and task-board templates
- [x] **Root cause 5 — Review fields undefined:** `review.status`, `review.reviewerType`, `comment.authorType`, `comment.severity`, `comment.category` can all be undefined
- [x] **Fix 5:** Added null guards on all review-related dynamic translation keys in review-detail and review-list templates
- [x] **Root cause 6 — Agent chat connectionState undefined:** `connectionState()` can return undefined before initialization
- [x] **Fix 6:** Added null guard wrapping the entire connection status display
- [x] **Files modified:** `task-detail.component.html`, `task-board.component.html`, `task-board.component.ts` (`getStateBadge()`), `task-card.component.ts`, `review-detail.component.html`, `review-list.component.html`, `agent-chat.component.html`, `en.json`, `fr.json`

#### Build & Test Verification
- [x] `mvn clean test` — all backend modules BUILD SUCCESS, 0 failures, 0 errors (435 test classes)
- [x] All 1,123 Angular tests passing (0 failures)
- [x] Angular build passing (production configuration)

### Phase 21: Docker Workspace Fix, TaskState Alignment & Agent Panel

#### Goal 1: Docker Workspace Fix ("Failed to create Docker workspace")
- [x] **Root cause:** Read-only rootfs in Docker workspace containers blocks `apt-get install git` at runtime. `initializeWorkspaceUser()` and `ensureGitInstalled()` fail silently, then `testGitAccess()` fails.
- [x] **Fix:** Created `deploy/docker/Dockerfile.workspace-base` — Ubuntu 22.04 base with git, openssh-client, ca-certificates pre-installed, squadron user (UID/GID 1000), /workspace directory
- [x] `DockerWorkspaceProvider.java` — Added `DEFAULT_BASE_IMAGE = "squadron/workspace-base:latest"`, `defaultBaseImage` config field, all 3 constructors updated, `createContainer()` uses `defaultBaseImage`
- [x] `initializeWorkspaceUser()` — checks `which git` first; if git found, skips package install entirely
- [x] `WorkspaceGitService.testGitAccess()` — no longer hardcodes `ubuntu:22.04`; uses provider's configured default
- [x] `ensureGitInstalled()` — improved logging, warns on failure instead of silently failing
- [x] `application.yml` — Added `squadron.workspace.docker.base-image` config
- [x] `docker-compose.yml` — Added `SQUADRON_WORKSPACE_BASE_IMAGE` env var
- [x] `testldap-build-and-start.sh` — Added workspace-base image build step before service images
- [x] Tests: All 229 workspace tests passing

#### Goal 2: TaskState Alignment (Backend 8→9 states to match Frontend)
- [x] **Change:** Added `IN_PROGRESS` to backend `TaskState` enum between `PROPOSE_CODE` and `REVIEW`
- [x] `TaskState.java` — Now 9 values: BACKLOG, PRIORITIZED, PLANNING, PROPOSE_CODE, IN_PROGRESS, REVIEW, QA, MERGE, DONE
- [x] `WorkflowEngine.java` — Default transitions updated (15 transitions): PROPOSE_CODE → IN_PROGRESS, IN_PROGRESS → REVIEW
- [x] `DefaultWorkflowInitializer.java` — Same transition updates
- [x] `TaskStateDispatcher.java` — Updated comments (IN_PROGRESS is NOT agent-dispatched)
- [x] Tests updated: TaskStateTest (count 8→9), WorkflowEngineTest (transitions), DefaultWorkflowInitializerTest (13→15), ProjectWorkflowMappingServiceTest (8→9 states), WorkflowEndToEndTest (lifecycle routes through IN_PROGRESS)
- [x] All 401 orchestrator tests passing

#### Goal 3: Agent Interaction Workflow — Task Detail Agent Panel
- [x] **Backend (squadron-agent):**
  - `ConversationSummaryDto.java` — New DTO (id, taskId, agentType, status, provider, model, totalTokens, messageCount, createdAt, updatedAt)
  - `ConversationService.java` — Added `getConversationSummaries(UUID taskId)` and `getActiveConversationForTask(UUID taskId)`
  - `AgentChatController.java` — Added `GET /api/agents/chat/task/{taskId}/summaries` endpoint
  - Tests: ConversationSummaryDtoTest (7), ConversationServiceTest (+6), AgentChatControllerTest (+4)
  - All 967 agent tests passing
- [x] **Frontend (squadron-ui):**
  - `agent.model.ts` — Added `ConversationSummary` interface
  - `agent.service.ts` — Added `getConversationSummaries(taskId)` method
  - `agent.service.spec.ts` — Added 2 tests for getConversationSummaries
  - `task-detail.component.ts` — Rewritten with agent panel: injects AgentService, UserSquadronService, WebSocketService, Router; loads conversation summaries, agent session, squadron agents; has live progress subscription; provides openAgentChat(), toggleAgentSelector(), conversationStatusClass()
  - `task-detail.component.html` — Rewritten with agent activity panel: live progress bar OR session status, conversation history list with agent type/status/messages/tokens/model info, "Open Chat" button
  - `task-detail.component.scss` — Rewritten with agent panel styles (progress bar, session status, conversation list, status indicators)
  - `task-detail.component.spec.ts` — Rewritten with 22 tests (8 original + 14 new for agent panel features)
  - `en.json` + `fr.json` — Added `tasks.detail.agent.*` i18n keys (15 keys)
  - Template fix: `@else if (agentSession(); as session)` → `@else if (agentSession())` (Angular `as` only allowed on primary `@if`)
  - Test fix: Removed `{ provide: Router, useValue: routerSpy }` — used `TestBed.inject(Router)` + `spyOn(router, 'navigate')` to avoid RouterLink constructor error
  - All 1,137 Angular tests passing

#### Build & Test Verification (Phase 21)
- [x] `mvn clean test` — all backend modules BUILD SUCCESS, 0 failures, 0 errors
- [x] All 1,137 Angular tests passing (0 failures)
- [x] Angular build passing (production configuration)

---

## Quick Reference

| Module | Sources | Tests | Status |
|--------|:-------:|:-----:|--------|
| squadron-common | 72 | 70 | Complete (606 tests) |
| squadron-gateway | 11 | 10 | Complete (133 tests) |
| squadron-identity | 43 | 43 | Complete (461 tests) |
| squadron-orchestrator | 39 | 39 | Complete (401 tests) |
| squadron-agent | 99 | 99 | Complete (967 tests) |
| squadron-workspace | 20 | 19 | Complete (229 tests) |
| squadron-platform | 48 | 46 | Complete (550 tests) |
| squadron-git | 37 | 38 | Complete (371 tests) |
| squadron-review | 33 | 33 | Complete (245 tests) |
| squadron-config | 12 | 12 | Complete (106 tests) |
| squadron-notification | 25 | 26 | Complete (168 tests) |
| **TOTAL** | **439** | **435** | **4,237 tests passing** |
| squadron-ui | 34 components | 62 specs | Complete (1,137 tests) |
