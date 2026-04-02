# Squadron - Implementation Progress Tracker

**Last updated:** 2026-04-02
**Current Status:** All 11 modules fully implemented with tests. All post-launch features complete (Features 1-15). Feature 15: SSH Key Management + Setup Wizard — new `SshKey` entity with encrypted private keys, full CRUD at `/api/platforms/ssh-keys`, `platform_category` column on `platform_connections` (TICKET_PROVIDER/GIT_REMOTE), `branch_naming_template` on projects, frontend project-config rewritten as 4-step wizard (Ticket Providers → Git Remotes + SSH Keys → Projects → Branch & Workflow). All previous features and bug fixes intact. All backend tests passing (474 platform, 328 orchestrator). All 798 Angular tests passing (0 failures). Angular build passing.

---

## Completed Modules

### squadron-common (66 src / 64 test)
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
- [x] Flyway migrations (V1, V2)
- [x] All tests passing (including 3 new TenantController tests, 5 new TenantService tests)

### squadron-orchestrator (39 src / 36 test)
- [x] Custom PostgreSQL state machine (WorkflowEngine)
- [x] Task/Project/Workflow CRUD
- [x] State transitions with validation
- [x] TaskSyncService
- [x] DefaultWorkflowInitializer
- [x] PlatformServiceClient (Feign)
- [x] ResilientPlatformServiceClient (circuit breaker + retry wrapper)
- [x] Project workflow mappings (entity, repository, service, controller endpoints)
- [x] `branchNamingTemplate` field on `Project` entity and `CreateProjectRequest` DTO
- [x] Flyway migrations (V1, V2, V3, V4)
- [x] All 328 tests passing

### squadron-agent (90 src / 91 test)
- [x] Agent providers (OpenAI-compatible, Ollama)
- [x] Tool system (ToolRegistry, ToolExecutionEngine, built-in tools)
- [x] Services (Agent, Planning, Coding, Review, QA, Merge, Coverage)
- [x] Conversation management
- [x] Squadron config management
- [x] Token usage tracking
- [x] WebSocket controller
- [x] Agent dashboard API (DTOs, service, controller, 17 tests)
- [x] User agent squadron configuration (entity, DTO, repository, service, controller, migration, 28 tests)
- [x] Listeners migrated to JetStreamSubscriber (Planning, Coding, Review, QA, Merge, PlanApproval)
- [x] Feign clients (OrchestratorClient, GitServiceClient, ReviewServiceClient, WorkspaceServiceClient)
- [x] Resilient Feign wrappers (circuit breaker + retry for all 4 Feign clients)
- [x] Flyway migrations (V1, V2, V3)
- [x] All tests passing

### squadron-workspace (16 src / 16 test)
- [x] Workspace providers (Kubernetes, Docker)
- [x] WorkspaceService with lifecycle management
- [x] WorkspaceGitService
- [x] WorkspaceCleanupScheduler
- [x] Flyway migration (V1)
- [x] All tests passing

### squadron-platform (35 src / 35 test)
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
- [x] `platform_category` column on `platform_connections` (TICKET_PROVIDER / GIT_REMOTE), auto-determined by platform type
- [x] `PlatformConnectionService.listConnectionsByTenantAndCategory()` + `GET /tenant/{tenantId}/category/{category}` endpoint
- [x] Flyway migrations (V1, V2, V3, V4, V5, V6)
- [x] All 474 tests passing

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

### squadron-ui (Angular 21) — 798 tests passing
- [x] 33 components (dashboard, tasks, projects, reviews, agent-chat, squadron-config, user-tokens, etc.)
- [x] 25 services (including agent-dashboard, user-squadron, user-token, ssh-key, platform services)
- [x] 15 models (including agent dashboard, squadron config, user-token, RemoteProject, SshKey interfaces)
- [x] Auth infrastructure (guard, interceptor with token refresh on 401, OIDC)
- [x] Shared components (header, sidebar, avatar, notification-bell)
- [x] Admin console (users, teams, security groups, permissions, etc.)
- [x] Project config rewritten as 4-step setup wizard: Ticket Providers → Git Remotes + SSH Keys → Projects → Branch & Workflow
- [x] Agent-focused dashboard redesign (active/idle agents, active work, timeline, type breakdown)
- [x] Ticket provider integration UI (connection linking, remote status fetch, status-aware mappings)
- [x] User agent squadron configuration UI (agent cards, add/edit/remove/reset, inline template)
- [x] Whimsical default agent names (Architect, Maverick, Hawkeye, Gremlin, Stitch, Radar, Phoenix, Oracle)
- [x] Unified settings page: 6-tab layout (General, Providers & Projects, Agent Squadron, Notifications, Agent Config, Platform Tokens)
- [x] User Platform Tokens tab: link/unlink PAT and OAuth2 accounts, view linked accounts per user
- [x] Project config labels generalized from ticket-specific to platform-inclusive
- [x] Project card headers enhanced with platform type badge, connection status indicator, improved mapping labels

### Infrastructure
- [x] Docker Compose (docker-compose.yml)
- [x] Parent POM with dependency management
- [x] All 24 Flyway migrations (V1-V6 for platform, V1-V4 for orchestrator, V1-V3 for identity/agent/git, V1-V2 for review/notification, V1 for config/workspace)
- [x] Test LDAP integration (docker-compose-testldap.yml, seed data)
- [x] All 19 containers healthy with testldap-build-and-start.sh

---

## Completed Inter-Service Communication

### OpenFeign Clients
- [x] FeignConfig + FeignErrorDecoder in squadron-common
- [x] OrchestratorClient (squadron-agent -> squadron-orchestrator)
- [x] GitServiceClient (squadron-agent -> squadron-git)
- [x] ReviewServiceClient (squadron-agent -> squadron-review)
- [x] WorkspaceServiceClient (squadron-agent -> squadron-workspace)
- [x] PlatformServiceClient (squadron-orchestrator -> squadron-platform)
- [x] Feign URL properties configured in all application.yml files
- [x] Resilient wrappers with circuit breaker + retry for all 5 Feign clients

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

---

## Quick Reference

| Module | Sources | Tests | Status |
|--------|:-------:|:-----:|--------|
| squadron-common | 66 | 64 | Complete |
| squadron-gateway | 11 | 11 | Complete |
| squadron-identity | 42 | 42 | Complete |
| squadron-orchestrator | 39 | 36 | Complete |
| squadron-agent | 90 | 91 | Complete |
| squadron-workspace | 16 | 16 | Complete |
| squadron-platform | 42 | 42 | Complete |
| squadron-git | 34 | 36 | Complete |
| squadron-review | 26 | 27 | Complete |
| squadron-config | 11 | 11 | Complete |
| squadron-notification | 24 | 24 | Complete |
| **TOTAL** | **401** | **400** | |
| squadron-ui | 33 components | 57 specs | Complete |
