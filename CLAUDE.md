# CLAUDE.md - Instructions for AI Assistants Working on Squadron

## Project Overview

Squadron is an AI-powered software development workflow platform. It integrates with
ticketing systems (JIRA, GitHub Issues, GitLab Issues, Azure DevOps) and Git platforms
(GitHub, GitLab, Bitbucket) to automate planning, coding, code review, and QA using
configurable AI agent squadrons.

## Critical Rules

1. **DO NOT STOP during compactions.** If you experience a context compaction, resume
   exactly where you left off. Re-read the TODO list and continue with the next
   incomplete task.

2. **Use as many subagents as needed.** Launch parallel Task agents aggressively to
   maximize throughput. Premium tokens are authorized -- use them freely.

3. **DO NOT STOP until you are done.** Complete all tasks in the TODO list. If a task
   fails, fix it and continue. Never leave the project in a broken state.

4. **Always verify your work compiles.** Run `mvn compile -q` after making changes to
   catch errors early. Run `mvn verify -q` when completing a module.

5. **Always add tests.** Every class you write must have corresponding unit tests.
   Services, controllers, and engine classes need integration tests. Run
   `mvn test -q` to verify all tests pass before moving on.

6. **All tests must pass.** Never leave the project with failing tests. If a test
   fails, fix it before continuing. Run `mvn verify -q` to confirm.

7. **Create pull requests when code is ready.** After implementing a feature or
   completing a module, create a pull request on the appropriate Git platform.
   Squadron supports GitHub, GitLab, and Bitbucket -- use whichever platform the
   repository is hosted on.

## Architecture

See `ARCHITECTURE.md` for the full architecture plan.

## Technology Stack

- **Backend:** Spring Boot 3.5.3, Java 21 LTS
- **AI/LLM:** Spring AI 1.0.0
- **Frontend:** Angular 21.x
- **Database:** PostgreSQL 17 + PgBouncer + HikariCP
- **Cache:** Redis 7.x
- **Message Broker:** NATS with JetStream
- **Identity:** Keycloak 26.x
- **Monitoring:** Prometheus + Grafana
- **Containers:** Kubernetes (preferred) / Docker (fallback)
- **Certificates:** HashiCorp Vault + cert-manager
- **Build Tool:** Maven 3.9.x

## Project Structure

Maven multi-module monorepo:

```
squadron/
  pom.xml                    # Parent POM
  squadron-common/           # Shared DTOs, security, utilities
  squadron-gateway/          # API Gateway (Spring Cloud Gateway)
  squadron-orchestrator/     # Task workflow engine (custom PostgreSQL state machine)
  squadron-agent/            # AI Agent Service (Spring AI)
  squadron-workspace/        # Sandboxed container management (K8s/Docker)
  squadron-platform/         # Ticketing platform adapters
  squadron-git/              # Git operations + platform APIs
  squadron-review/           # Code review orchestration
  squadron-config/           # Centralized configuration service
  squadron-notification/     # Event-driven notifications
  squadron-identity/         # Tenant/user management + Keycloak
  squadron-ui/               # Angular 21 frontend
  deploy/                    # Docker Compose, Helm charts, Terraform
```

## Key Patterns

- **Adapter Pattern** for ticketing platforms and Git platforms
- **Custom PostgreSQL state machine** for workflow (NOT Temporal, NOT Spring Statemachine)
- **Hierarchical configuration:** default -> tenant -> team -> user (most specific wins)
- **Row-level multi-tenancy** with `tenant_id` on all tables
- **Delegated user identity** -- use the user's own OAuth tokens when calling external APIs
- **NATS JetStream** for async inter-service events
- **Spring Cloud OpenFeign** for sync inter-service REST calls
- **STOMP over WebSocket** for real-time frontend communication

## Package Naming Convention

All Java packages use: `com.squadron.<module>.<subpackage>`

Examples:
- `com.squadron.common.dto`
- `com.squadron.orchestrator.engine`
- `com.squadron.agent.service`
- `com.squadron.platform.adapter.jira`

## Database Conventions

- All tables have `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
- All tables have `tenant_id UUID NOT NULL` for multi-tenancy
- All tables have `created_at TIMESTAMP NOT NULL DEFAULT NOW()`
- Use `JSONB` for flexible/configurable fields
- Flyway migrations in `src/main/resources/db/migration/` with naming: `V1__description.sql`

## Testing Conventions

- **Unit tests** with JUnit 5 + Mockito for all services, controllers, engines, adapters, and utilities
- **Integration tests** with Testcontainers (PostgreSQL, Redis, NATS) for repository layers and service interactions
- **E2E tests** for critical workflows (task lifecycle, agent conversation flow, platform sync)
- Test classes in `src/test/java/` mirroring the main source structure
- **Every class must have tests.** No exceptions.
- Target: 100% line coverage for business logic; reasonable coverage for configuration classes
- Use `@SpringBootTest` for integration tests, `@WebMvcTest` for controller unit tests
- Use `@DataJpaTest` with Testcontainers for repository integration tests
- Name tests descriptively: `should_createTenant_when_validRequest()`, `should_throwNotFound_when_tenantMissing()`

## Build Commands

```bash
mvn clean compile        # Compile all modules
mvn clean test           # Run unit tests
mvn clean verify         # Run all tests including integration
mvn clean package        # Build JARs
mvn clean install        # Install to local repo
mvn spring-boot:run      # Run a specific service (from its module directory)
```

## Progress Tracking with TODO.md

A `TODO.md` file in the project root tracks all implementation progress. This is the
single source of truth for what has been done and what remains.

### Maintaining TODO.md

- **Before starting work:** Read `TODO.md` to understand current state
- **While working:** Update `TODO.md` after completing each module or significant task.
  Mark items with `[x]` when done. Add new items as they're discovered.
- **After completing work:** Update the "Last updated" date, "Current Status" section,
  and "Next Steps" list. Ensure it accurately reflects reality.
- **Keep it accurate:** If a module's tests are written, mark it complete. If new source
  files are added, update the counts. If new modules are started, add entries.

### When Resuming After Interruption or Compaction

1. **Read `TODO.md`** -- this tells you exactly what's done and what's next
2. Check `git status` and `git log --oneline -5` to see recent commits
3. Run `mvn compile -q` to verify current state compiles
4. Run `mvn test -q` to verify all existing tests pass
5. Pick up the next incomplete `[ ]` item from TODO.md
6. DO NOT re-do completed `[x]` work
7. Update `TODO.md` as you make progress
