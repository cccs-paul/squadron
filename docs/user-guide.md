# Squadron User Guide

## Getting Started

### What is Squadron?

Squadron is an AI-powered software development workflow platform. It connects to your
existing ticketing system (JIRA, GitHub Issues, GitLab Issues, Azure DevOps) and Git
platform (GitHub, GitLab, Bitbucket) to automate planning, coding, code review, and
quality assurance using configurable AI agents.

### First Login

1. Navigate to the Squadron dashboard URL provided by your administrator
2. Click **Sign In** — you will be redirected to the identity provider (Keycloak or
   your corporate SSO)
3. Authenticate with your credentials
4. On first login, you may be prompted to:
   - Select your tenant/organization (if multi-tenant)
   - Join a team
   - Link your ticketing platform account
   - Link your Git platform account

### Dashboard Overview

After logging in, you will see the main dashboard with:

- **Task Board** — Kanban-style board showing tasks in each workflow state
- **Navigation Sidebar** — Access to tasks, agent chat, reviews, settings, and admin
- **Notification Bell** — Real-time notifications for task updates, reviews, and more

## Creating and Managing Tasks

### Importing Tasks from Ticketing Platforms

Tasks are typically imported automatically from your connected ticketing platform:

1. Go to **Settings > Platform Connections**
2. Connect your ticketing platform (see "Setting Up Ticketing Platform Connections")
3. Select the project to sync
4. Tasks will appear in the **Backlog** column of your task board

### Creating Tasks Manually

1. Click the **+ New Task** button on the task board
2. Fill in:
   - **Title** — Brief description of the work
   - **Description** — Detailed requirements, acceptance criteria
   - **Priority** — LOW, MEDIUM, HIGH, or CRITICAL
   - **Labels** — Tags for categorization
3. Click **Create** — the task appears in the Backlog column

### Task Workflow States

Tasks progress through these states:

| State | Description | What to Do |
|---|---|---|
| **Backlog** | Imported or created, not yet prioritized | Review and prioritize |
| **Prioritized** | Ready for work | Move to Planning when ready |
| **Planning** | AI planning agent creates implementation plan | Review the plan, refine via chat, approve |
| **Propose Code** | AI coding agent implements the plan | Wait for code generation, review diff |
| **Review** | Code is being reviewed (AI + human) | Review code, add comments, approve |
| **QA** | QA agent analyzes test coverage | Review QA report, address findings |
| **Merge** | Code is being merged | Automatic; resolves when merged |
| **Done** | Complete, synced back to ticketing platform | — |

### Moving Tasks Between States

- **Drag and drop** tasks between columns on the task board
- Or click a task, then click **Transition > [Target State]**
- Some transitions have guards:
  - Moving to **Review** requires an approved plan
  - Moving to **QA** requires code changes (PR exists)
  - Moving to **Merge** requires all reviews approved and QA passed

## Configuring AI Agents

### Understanding Agent Types

Squadron uses four specialized AI agents:

| Agent | Purpose | When Active |
|---|---|---|
| **Planning** | Analyzes the task and codebase, proposes an implementation plan | PLANNING state |
| **Coding** | Implements the approved plan, generates code changes | PROPOSE_CODE state |
| **Review** | Reviews code changes, identifies issues | REVIEW state |
| **QA** | Analyzes test coverage, generates missing tests | QA state |

### Configuring Your Squadron

1. Go to **Settings > Agent Configuration**
2. For each agent type, configure:
   - **Provider** — Which AI service to use (OpenAI, Ollama, etc.)
   - **Model** — Specific model (e.g., `gpt-4o`, `claude-sonnet-4-20250514`)
   - **Max Tokens** — Maximum response length
   - **Temperature** — Creativity level (0.0 = deterministic, 1.0 = creative)
   - **System Prompt Override** — Custom instructions for the agent

3. Additional settings:
   - **Max Concurrent Agents** — How many agents can run simultaneously
   - **Daily Token Budget** — Maximum tokens per day (controls cost)

### Configuration Hierarchy

Settings are resolved hierarchically (most specific wins):

```
Default → Tenant → Team → User
```

Your personal settings override team settings, which override tenant defaults.

## Setting Up Ticketing Platform Connections

### Connecting JIRA Cloud

1. Go to **Settings > Platform Connections > Add Connection**
2. Select **JIRA Cloud**
3. Enter your Atlassian site URL (e.g., `https://your-org.atlassian.net`)
4. Click **Authorize** — you'll be redirected to Atlassian to grant access
5. Select the projects to sync
6. Click **Save**

### Connecting JIRA Server/Data Center

1. Go to **Settings > Platform Connections > Add Connection**
2. Select **JIRA Server**
3. Enter your JIRA server URL
4. Choose auth method:
   - **Personal Access Token (PAT)** — Recommended. Generate in JIRA:
     Profile > Personal Access Tokens
   - **Basic Auth** — Username + password
5. Click **Test Connection** to verify
6. Click **Save**

### Connecting GitHub Issues

1. Go to **Settings > Platform Connections > Add Connection**
2. Select **GitHub**
3. Click **Authorize with GitHub** — installs the Squadron GitHub App
4. Select the repositories to sync
5. Click **Save**

### Connecting GitLab Issues

1. Go to **Settings > Platform Connections > Add Connection**
2. Select **GitLab**
3. Enter your GitLab instance URL
4. Click **Authorize** — OAuth2 flow
5. Select projects to sync
6. Click **Save**

### Connecting Azure DevOps

1. Go to **Settings > Platform Connections > Add Connection**
2. Select **Azure DevOps**
3. Enter your organization URL (e.g., `https://dev.azure.com/your-org`)
4. Choose auth method: OAuth2 or Personal Access Token
5. Select the projects to sync
6. Click **Save**

## Setting Up Git Platform Connections

Git platform connections are used for creating pull/merge requests and merging code.

### GitHub

If you connected GitHub Issues above, the Git connection is included. Otherwise:

1. Go to **Settings > Git Connections > Add Connection**
2. Select **GitHub**
3. Authorize via GitHub App or OAuth
4. Select repositories

### GitLab

1. Go to **Settings > Git Connections > Add Connection**
2. Select **GitLab**
3. Authorize via OAuth2
4. Select projects

### Bitbucket

1. Go to **Settings > Git Connections > Add Connection**
2. Select **Bitbucket**
3. Authorize via OAuth2 (App Passwords for Bitbucket Server)
4. Select repositories

### Configuring Branch Strategies

1. Go to **Settings > Git > Branch Strategies**
2. For each team/project, configure:
   - **Strategy Type**: GitFlow, Trunk-Based, or Custom
   - **Branch Template**: e.g., `feature/{slug}`, `dev/{ticket-id}`
   - **Default Branch**: `main`, `master`, or custom
   - **Merge Method**: Merge Commit, Squash, or Rebase

## Configuring Notification Preferences

1. Go to **Settings > Notifications**
2. Toggle notification channels:
   - **In-App** — Notifications in the Squadron dashboard
   - **Email** — Email notifications
   - **Slack** — Webhook-based Slack notifications
   - **Teams** — Webhook-based Microsoft Teams notifications
3. For Slack/Teams, enter the webhook URL for your channel
4. **Mute specific event types** to reduce noise:
   - Task state changes
   - Review requests
   - Agent completions
   - PR events
5. Click **Save**

## Understanding the Workflow Pipeline

### The Full Cycle

```
1. Ticket appears in Backlog
        ↓
2. Team lead prioritizes → Prioritized
        ↓
3. Developer moves to Planning
   → Workspace spins up
   → Planning agent analyzes codebase + ticket
   → Proposes implementation plan
        ↓
4. Developer reviews plan in Agent Chat
   → Refines via conversation
   → Approves plan
        ↓
5. Task moves to Propose Code
   → Coding agent implements the plan
   → Creates code changes in workspace
   → Generates PR/MR on Git platform
        ↓
6. Task moves to Review
   → AI review agent provides automated review
   → Human reviewers are notified
   → Comments are posted on the PR
   → All required approvals collected
        ↓
7. Task moves to QA
   → QA agent runs test coverage analysis
   → Identifies test gaps
   → Optionally generates missing tests
   → QA report published
        ↓
8. Task moves to Merge
   → Checks mergeability (no conflicts)
   → Merges PR with configured strategy
        ↓
9. Task moves to Done
   → Status synced back to ticketing platform
```

### Working with the Planning Agent

When a task enters the **Planning** state:

1. A workspace is created with the repository code
2. The planning agent analyzes the ticket and codebase
3. An implementation plan appears in the **Agent Chat** panel

To interact with the planning agent:
1. Open the task and click the **Agent Chat** tab
2. Read the proposed plan
3. Ask questions or request changes:
   - "Can you break step 3 into smaller tasks?"
   - "What about error handling for the API calls?"
   - "Use the repository pattern instead"
4. When satisfied, click **Approve Plan**
5. The task automatically transitions to Propose Code

### Working with the Coding Agent

When a task enters **Propose Code**:

1. The coding agent reads the approved plan
2. It implements the plan using tool-calling:
   - Reads existing code
   - Writes new files
   - Runs tests
   - Iterates up to 25 times
3. Changes are committed and a PR is created
4. View the diff in the **Diff Viewer** tab

## Reviewing AI-Generated Code

### The Review Interface

When a task is in the **Review** state:

1. Open the task and click the **Review** tab
2. The interface shows:
   - **Diff View** — Line-by-line code changes with syntax highlighting
   - **AI Comments** — Automated review findings (security, performance, etc.)
   - **Human Comments** — Comments from human reviewers
   - **Unresolved/Resolved** counts

### Adding Review Comments

1. In the Diff View, click on a line number to add an inline comment
2. Write your comment
3. Select severity: Info, Warning, Error, Critical
4. Click **Submit**

### Submitting Your Review

1. After reviewing all changes, click **Submit Review**
2. Choose your verdict:
   - **Approve** — Code is ready
   - **Request Changes** — Issues need to be addressed
   - **Comment** — General feedback, no blocking

### Review Gates

The task can only proceed to QA when:
- All required human approvals are collected (configured per team)
- AI review is complete (if required by policy)

## Managing QA Reports

### Understanding the QA Report

When a task enters the **QA** state, the QA agent analyzes test coverage:

1. Open the task and click the **QA Report** tab
2. The report shows:
   - **Overall Coverage** — Percentage with progress bar
   - **Line Coverage** and **Branch Coverage**
   - **Tests Passed / Failed / Skipped** — With visual breakdown
   - **Findings** — Issues by severity (Info, Warning, Error, Critical)

### QA Findings

Common finding types:
- **MISSING_TEST** — Functions or branches without test coverage
- **LOW_COVERAGE** — Coverage below the configured threshold
- **SECURITY** — Security-related code without adequate testing
- **PERFORMANCE** — Performance-sensitive code without benchmarks

### QA Gate

The task proceeds to Merge only when:
- Overall coverage meets the threshold (configurable, default 80%)
- No CRITICAL findings remain
- All tests pass

## Admin Operations

### Tenant Management (Admin Only)

1. Go to **Admin > Tenants**
2. Create, update, or deactivate tenants
3. Configure tenant-level settings:
   - Default workflow definition
   - Default squadron configuration
   - Token budgets
   - Platform connection limits

### Team Management

1. Go to **Admin > Teams**
2. Create teams within your tenant
3. Add/remove team members
4. Assign team roles: Team Lead, Developer, QA, Viewer

### Usage Tracking

1. Go to **Admin > Usage Dashboard**
2. View:
   - **Total tokens used** — Across all agents
   - **Cost estimate** — Based on provider pricing
   - **Invocations** — Number of agent runs
   - **Breakdown by agent type** — Planning, Coding, Review, QA
3. Filter by time period, team, or user
4. Use this to manage token budgets and optimize model selection

### User Management

1. Go to **Admin > Users**
2. View all users in your tenant
3. Change roles: ADMIN, TEAM_LEAD, DEVELOPER, QA, VIEWER
4. Deactivate users (preserves audit trail)

### Audit Trail

1. Go to **Admin > Audit Log**
2. View all actions:
   - Task state transitions
   - Configuration changes
   - Platform connection modifications
   - Agent invocations
3. Filter by user, action type, resource, or time range
4. Export for compliance reporting
