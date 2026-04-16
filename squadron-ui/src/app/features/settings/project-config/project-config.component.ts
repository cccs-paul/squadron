import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ProjectService } from '../../../core/services/project.service';
import { PlatformService } from '../../../core/services/platform.service';
import { SshKeyService } from '../../../core/services/ssh-key.service';
import { ReviewBotConfigService } from '../../../core/services/review-bot-config.service';
import { WorkspaceService, TestGitAccessResult } from '../../../core/services/workspace.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Project, RemoteProject, WorkflowMapping, BranchStrategyType } from '../../../core/models/project.model';
import {
  PlatformConnection,
  PlatformConnectionType,
  PlatformCategory,
  ConnectionStatus,
  CreateConnectionRequest,
  SshKey,
  CreateSshKeyRequest,
  KeyUsage,
  ReviewBotConfig,
  CreateReviewBotConfigRequest,
} from '../../../core/models/security.model';
import { forkJoin, of, catchError } from 'rxjs';

export type WizardStep = 'ticket-providers' | 'git-remotes' | 'projects' | 'branch-workflow';

interface ProviderForm {
  name: string;
  platformType: string;
  baseUrl: string;
  authType: string;
  credentials: Record<string, string>;
}

interface SshKeyForm {
  connectionId: string;
  name: string;
  publicKey: string;
  privateKey: string;
  keyType: string;
  keyUsage: string;
}

interface ImportCandidate {
  remote: RemoteProject;
  selected: boolean;
  name: string;
  description: string;
}

interface ProjectEditForm {
  name: string;
  description: string;
  connectionId: string;
}

export interface GitAccessStep {
  label: string;
  status: 'pending' | 'running' | 'done' | 'error';
  detail?: string;
  startedAt?: number;
  completedAt?: number;
}

interface ProjectMappingState {
  project: Project;
  expanded: boolean;
  mappings: WorkflowMapping[];
  mappingsLoaded: boolean;
  remoteStatuses: string[];
  saving: boolean;
  saveSuccess: boolean;
  saveError: string | null;
  loading: boolean;
  fetchingStatuses: boolean;
  fetchError: string | null;
  connectionName: string | null;
}

/** Platform types that are ticket providers (not Git remotes) */
const TICKET_PROVIDER_TYPES = new Set(['JIRA_CLOUD', 'JIRA_SERVER', 'AZURE_DEVOPS']);
/** Platform types that are Git remotes */
const GIT_REMOTE_TYPES = new Set(['GITHUB', 'GITLAB', 'BITBUCKET']);
/** Cloud platforms that don't need a base URL */
const CLOUD_PLATFORMS = new Set(['GITHUB', 'GITLAB', 'JIRA_CLOUD']);

const AUTH_TYPE_OPTIONS: Record<string, { value: string; label: string; fields: { key: string; label: string; secret: boolean }[] }[]> = {
  JIRA_CLOUD: [
    { value: 'API Token', label: 'projectConfig.authTypes.apiToken', fields: [{ key: 'email', label: 'projectConfig.authFields.email', secret: false }, { key: 'apiToken', label: 'projectConfig.authFields.apiToken', secret: true }] },
    { value: 'OAuth 2.0', label: 'projectConfig.authTypes.oauth2', fields: [{ key: 'clientId', label: 'projectConfig.authFields.clientId', secret: false }, { key: 'clientSecret', label: 'projectConfig.authFields.clientSecret', secret: true }] },
  ],
  JIRA_SERVER: [
    { value: 'PAT', label: 'projectConfig.authTypes.pat', fields: [{ key: 'pat', label: 'projectConfig.authFields.personalAccessToken', secret: true }] },
    { value: 'Basic Auth', label: 'projectConfig.authTypes.basicAuth', fields: [{ key: 'username', label: 'projectConfig.authFields.username', secret: false }, { key: 'password', label: 'projectConfig.authFields.password', secret: true }] },
  ],
  GITHUB: [
    { value: 'PAT', label: 'projectConfig.authTypes.pat', fields: [{ key: 'pat', label: 'projectConfig.authFields.personalAccessToken', secret: true }] },
    { value: 'GitHub App', label: 'projectConfig.authTypes.app', fields: [{ key: 'appId', label: 'projectConfig.authFields.appId', secret: false }, { key: 'installationId', label: 'projectConfig.authFields.installationId', secret: false }, { key: 'privateKey', label: 'projectConfig.authFields.privateKey', secret: true }] },
  ],
  GITLAB: [
    { value: 'PAT', label: 'projectConfig.authTypes.pat', fields: [{ key: 'pat', label: 'projectConfig.authFields.personalAccessToken', secret: true }] },
  ],
  AZURE_DEVOPS: [
    { value: 'PAT', label: 'projectConfig.authTypes.pat', fields: [{ key: 'pat', label: 'projectConfig.authFields.personalAccessToken', secret: true }] },
  ],
  BITBUCKET: [
    { value: 'App Password', label: 'projectConfig.authTypes.appPassword', fields: [{ key: 'username', label: 'projectConfig.authFields.username', secret: false }, { key: 'password', label: 'projectConfig.authFields.appPassword', secret: true }] },
  ],
};

const BRANCH_STRATEGIES: { value: string; label: string; description: string }[] = [
  { value: 'TRUNK_BASED', label: 'projectConfig.branchStrategies.trunkBased.label', description: 'projectConfig.branchStrategies.trunkBased.description' },
  { value: 'GITFLOW', label: 'projectConfig.branchStrategies.gitflow.label', description: 'projectConfig.branchStrategies.gitflow.description' },
  { value: 'GITHUB_FLOW', label: 'projectConfig.branchStrategies.githubFlow.label', description: 'projectConfig.branchStrategies.githubFlow.description' },
  { value: 'GITLAB_FLOW', label: 'projectConfig.branchStrategies.gitlabFlow.label', description: 'projectConfig.branchStrategies.gitlabFlow.description' },
  { value: 'RELEASE_BRANCHING', label: 'projectConfig.branchStrategies.releaseBranching.label', description: 'projectConfig.branchStrategies.releaseBranching.description' },
];

const TICKET_PLATFORM_TYPES = ['JIRA_CLOUD', 'JIRA_SERVER', 'AZURE_DEVOPS'];
const GIT_PLATFORM_TYPES = ['GITHUB', 'GITLAB', 'BITBUCKET'];

@Component({
  selector: 'sq-project-config',
  standalone: true,
  imports: [FormsModule, TranslateModule],
  templateUrl: './project-config.component.html',
  styleUrl: './project-config.component.scss',
})
export class ProjectConfigComponent implements OnInit {
  private projectService = inject(ProjectService);
  private platformService = inject(PlatformService);
  private sshKeyService = inject(SshKeyService);
  private reviewBotConfigService = inject(ReviewBotConfigService);
  private workspaceService = inject(WorkspaceService);
  private authService = inject(AuthService);
  private translate = inject(TranslateService);

  loading = signal(true);
  loadError = signal<string | null>(null);
  activeStep = signal<WizardStep>('ticket-providers');

  // All connections (loaded once)
  allConnections = signal<PlatformConnection[]>([]);

  // Step 1: Ticket Providers
  ticketProviders = signal<PlatformConnection[]>([]);
  showTicketForm = signal(false);
  savingTicketProvider = signal(false);
  ticketSaveError = signal<string | null>(null);
  ticketSaveSuccess = signal(false);
  deletingConnectionId = signal<string | null>(null);
  editingTicketProviderId = signal<string | null>(null);
  ticketForm: ProviderForm = this.newTicketForm();

  // Step 2: Git Remotes
  gitRemotes = signal<PlatformConnection[]>([]);
  showGitForm = signal(false);
  savingGitRemote = signal(false);
  gitSaveError = signal<string | null>(null);
  gitSaveSuccess = signal(false);
  editingGitRemoteId = signal<string | null>(null);
  gitForm: ProviderForm = this.newGitForm();

  // Test Connection
  testingConnectionId = signal<string | null>(null);
  testConnectionResult = signal<{ id: string; success: boolean } | null>(null);

  // SSH Keys
  sshKeys = signal<SshKey[]>([]);
  showSshKeyForm = signal(false);
  savingSshKey = signal(false);
  sshKeySaveError = signal<string | null>(null);
  sshKeySaveSuccess = signal(false);
  deletingSshKeyId = signal<string | null>(null);
  sshKeyForm: SshKeyForm = this.newSshKeyForm();
  generatingDeployKey = signal(false);
  generatedPublicKey = signal<string | null>(null);
  copiedKey = signal(false);

  // Review Bot Configs
  reviewBotConfigs = signal<ReviewBotConfig[]>([]);
  showReviewBotForm = signal(false);
  savingReviewBot = signal(false);
  reviewBotSaveError = signal<string | null>(null);
  reviewBotSaveSuccess = signal(false);
  deletingReviewBotId = signal<string | null>(null);
  reviewBotForm = this.newReviewBotForm();

  // Step 3: Projects
  projectStates = signal<ProjectMappingState[]>([]);
  workflowStates = signal<string[]>([]);
  showImportPanel = signal(false);
  importConnectionId = signal<string>('');
  importLoading = signal(false);
  importError = signal<string | null>(null);
  importCandidates = signal<ImportCandidate[]>([]);
  importSaving = signal(false);
  importSaveError = signal<string | null>(null);
  importProgress = signal<{ done: number; total: number } | null>(null);
  importFetchComplete = signal(false);

  // Project edit/remove
  editingProjectId = signal<string | null>(null);
  deletingProjectId = signal<string | null>(null);
  showProjectEditForm = signal(false);
  savingProject = signal(false);
  projectSaveError = signal<string | null>(null);
  projectSaveSuccess = signal(false);
  projectEditForm: ProjectEditForm = this.newProjectEditForm();

  // Step 4: Branch & Workflow (uses projectStates)
  testingGitAccessProjectId = signal<string | null>(null);
  testGitAccessResult = signal<{ projectId: string; result: TestGitAccessResult } | null>(null);
  /** Step-by-step progress tracker for git access test */
  gitAccessSteps = signal<{ projectId: string; steps: GitAccessStep[] } | null>(null);
  gitAccessStepsExpanded = signal(false);

  readonly ticketPlatformTypes = TICKET_PLATFORM_TYPES;
  readonly gitPlatformTypes = GIT_PLATFORM_TYPES;
  readonly branchStrategies = BRANCH_STRATEGIES;

  readonly steps: { id: WizardStep; label: string; number: number }[] = [
    { id: 'ticket-providers', label: 'projectConfig.steps.ticketProviders', number: 1 },
    { id: 'git-remotes', label: 'projectConfig.steps.gitRemotes', number: 2 },
    { id: 'projects', label: 'projectConfig.steps.projects', number: 3 },
    { id: 'branch-workflow', label: 'projectConfig.steps.branchWorkflow', number: 4 },
  ];

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.loadError.set(null);

    const user = this.authService.user();
    if (!user) {
      this.loadError.set(this.translate.instant('projectConfig.errors.notAuthenticated'));
      this.loading.set(false);
      return;
    }

    forkJoin({
      projects: this.projectService.getProjectsByTenant(user.tenantId),
      states: this.projectService.getWorkflowStates(),
      connections: this.platformService.getConnectionsByTenant(user.tenantId),
      sshKeys: this.sshKeyService.getSshKeysByTenant(user.tenantId),
      reviewBotConfigs: this.reviewBotConfigService.getConfigsByTenant(user.tenantId),
    }).subscribe({
      next: ({ projects, states, connections, sshKeys, reviewBotConfigs }) => {
        this.workflowStates.set(states);
        this.allConnections.set(connections);
        this.sshKeys.set(sshKeys);
        this.reviewBotConfigs.set(reviewBotConfigs);
        this.categorizeConnections(connections);
        const initialStates = projects.map((p) => ({
          project: p,
          expanded: false,
          mappings: [] as WorkflowMapping[],
          mappingsLoaded: false,
          remoteStatuses: [] as string[],
          saving: false,
          saveSuccess: false,
          saveError: null,
          loading: false,
          fetchingStatuses: false,
          fetchError: null,
          connectionName: this.getConnectionName(p.connectionId, connections),
        }));
        this.projectStates.set(initialStates);
        this.loading.set(false);

        // Eagerly fetch mappings for all projects so collapsed cards show correct counts
        if (projects.length > 0) {
          const mappingRequests = projects.map((p) =>
            this.projectService.getWorkflowMappings(p.id).pipe(
              catchError(() => of([] as WorkflowMapping[])),
            ),
          );
          forkJoin(mappingRequests).subscribe({
            next: (allMappings) => {
              const updated = this.projectStates().map((ps, i) => ({
                ...ps,
                mappings: allMappings[i] ?? [],
                mappingsLoaded: true,
              }));
              this.projectStates.set(updated);
            },
          });
        }
      },
      error: () => {
        console.error('Failed to load project configuration data');
        this.workflowStates.set([]);
        this.allConnections.set([]);
        this.ticketProviders.set([]);
        this.gitRemotes.set([]);
        this.sshKeys.set([]);
        this.reviewBotConfigs.set([]);
        this.projectStates.set([]);
        this.loading.set(false);
      },
    });
  }

  setStep(step: WizardStep): void {
    this.activeStep.set(step);
  }

  getStepIndex(step: WizardStep): number {
    return this.steps.findIndex((s) => s.id === step);
  }

  isStepComplete(step: WizardStep): boolean {
    switch (step) {
      case 'ticket-providers': return this.ticketProviders().length > 0;
      case 'git-remotes': return this.gitRemotes().length > 0;
      case 'projects': return this.projectStates().length > 0;
      case 'branch-workflow': return this.projectStates().some((ps) => ps.mappings.length > 0);
      default: return false;
    }
  }

  nextStep(): void {
    const idx = this.getStepIndex(this.activeStep());
    if (idx < this.steps.length - 1) {
      this.activeStep.set(this.steps[idx + 1].id);
    }
  }

  prevStep(): void {
    const idx = this.getStepIndex(this.activeStep());
    if (idx > 0) {
      this.activeStep.set(this.steps[idx - 1].id);
    }
  }

  // --- Platform type helpers ---

  isCloudPlatform(platformType: string): boolean {
    return CLOUD_PLATFORMS.has(platformType);
  }

  getDefaultBaseUrl(platformType: string): string {
    switch (platformType) {
      case 'GITHUB': return 'https://api.github.com';
      case 'GITLAB': return 'https://gitlab.com';
      case 'JIRA_CLOUD': return '';
      default: return '';
    }
  }

  platformIcon(type: string): string {
    const keyMap: Record<string, string> = {
      'GITHUB': 'projectConfig.platforms.github',
      'GITLAB': 'projectConfig.platforms.gitlab',
      'JIRA_CLOUD': 'projectConfig.platforms.jiraCloud',
      'JIRA_SERVER': 'projectConfig.platforms.jiraServer',
      'AZURE_DEVOPS': 'projectConfig.platforms.azureDevops',
      'BITBUCKET': 'projectConfig.platforms.bitbucket',
    };
    const key = keyMap[type];
    return key ? this.translate.instant(key) : type;
  }

  platformDescription(type: string): string {
    const keyMap: Record<string, string> = {
      'JIRA_CLOUD': 'projectConfig.platformDescriptions.jiraCloud',
      'JIRA_SERVER': 'projectConfig.platformDescriptions.jiraServer',
      'AZURE_DEVOPS': 'projectConfig.platformDescriptions.azureDevops',
      'GITHUB': 'projectConfig.platformDescriptions.github',
      'GITLAB': 'projectConfig.platformDescriptions.gitlab',
      'BITBUCKET': 'projectConfig.platformDescriptions.bitbucket',
    };
    const key = keyMap[type];
    return key ? this.translate.instant(key) : '';
  }

  // ===== STEP 1: Ticket Providers =====

  getTicketAuthTypeOptions(): { value: string; label: string; fields: { key: string; label: string; secret: boolean }[] }[] {
    return AUTH_TYPE_OPTIONS[this.ticketForm.platformType] ?? [];
  }

  getTicketAuthFields(): { key: string; label: string; secret: boolean }[] {
    const options = this.getTicketAuthTypeOptions();
    const selected = options.find((o) => o.value === this.ticketForm.authType);
    return selected?.fields ?? [];
  }

  onTicketPlatformTypeChange(): void {
    const options = this.getTicketAuthTypeOptions();
    this.ticketForm.authType = options.length > 0 ? options[0].value : '';
    this.ticketForm.credentials = {};
    if (this.isCloudPlatform(this.ticketForm.platformType)) {
      this.ticketForm.baseUrl = this.getDefaultBaseUrl(this.ticketForm.platformType);
    } else {
      this.ticketForm.baseUrl = '';
    }
  }

  onTicketAuthTypeChange(): void {
    this.ticketForm.credentials = {};
  }

  toggleTicketForm(): void {
    this.showTicketForm.set(!this.showTicketForm());
    if (!this.showTicketForm()) {
      this.ticketForm = this.newTicketForm();
      this.ticketSaveError.set(null);
      this.editingTicketProviderId.set(null);
    }
  }

  editTicketProvider(conn: PlatformConnection): void {
    this.editingTicketProviderId.set(conn.id);
    this.ticketForm = {
      name: conn.name,
      platformType: conn.platformType,
      baseUrl: conn.baseUrl || '',
      authType: this.getFirstAuthType(conn.platformType, conn.authType),
      credentials: {},
    };
    this.showTicketForm.set(true);
    this.ticketSaveError.set(null);
  }

  canSaveTicketProvider(): boolean {
    const f = this.ticketForm;
    if (!f.name.trim() || !f.platformType || !f.authType) return false;
    if (!this.isCloudPlatform(f.platformType) && !f.baseUrl.trim()) return false;
    // When editing, credentials are optional (only sent if user fills them in)
    if (this.editingTicketProviderId()) return true;
    const fields = this.getTicketAuthFields();
    return fields.every((field) => (f.credentials[field.key] ?? '').trim().length > 0);
  }

  saveTicketProvider(): void {
    const user = this.authService.user();
    if (!user) return;

    this.savingTicketProvider.set(true);
    this.ticketSaveError.set(null);

    const editingId = this.editingTicketProviderId();

    if (editingId) {
      // Update existing connection
      const payload: Partial<PlatformConnection> = {
        name: this.ticketForm.name.trim(),
        platformType: this.ticketForm.platformType as any,
        baseUrl: this.ticketForm.baseUrl.trim() || this.getDefaultBaseUrl(this.ticketForm.platformType),
        authType: this.ticketForm.authType,
      };

      // Only include credentials if user actually filled them in
      const filledCredentials: Record<string, string> = {};
      let hasCredentials = false;
      for (const [key, value] of Object.entries(this.ticketForm.credentials)) {
        if (value && value.trim()) {
          filledCredentials[key] = value.trim();
          hasCredentials = true;
        }
      }
      if (hasCredentials) {
        (payload as any).credentials = filledCredentials;
      }

      this.platformService.updateConnection(editingId, payload).subscribe({
        next: (updated) => {
          this.ticketProviders.set(this.ticketProviders().map((c) => c.id === updated.id ? updated : c));
          this.allConnections.set(this.allConnections().map((c) => c.id === updated.id ? updated : c));
          this.savingTicketProvider.set(false);
          this.showTicketForm.set(false);
          this.ticketForm = this.newTicketForm();
          this.editingTicketProviderId.set(null);
          this.ticketSaveSuccess.set(true);
          setTimeout(() => this.ticketSaveSuccess.set(false), 3000);
        },
        error: (err: any) => {
          const msg = err?.error?.message || this.translate.instant('projectConfig.ticketProviders.errors.saveFailed');
          this.ticketSaveError.set(msg);
          this.savingTicketProvider.set(false);
        },
      });
    } else {
      // Create new connection
      const request: CreateConnectionRequest = {
        tenantId: user.tenantId,
        name: this.ticketForm.name.trim(),
        platformType: this.ticketForm.platformType,
        baseUrl: this.ticketForm.baseUrl.trim() || this.getDefaultBaseUrl(this.ticketForm.platformType),
        authType: this.ticketForm.authType,
        credentials: { ...this.ticketForm.credentials },
      };

      this.platformService.createConnectionFromRequest(request).subscribe({
        next: (connection) => {
          this.ticketProviders.set([...this.ticketProviders(), connection]);
          this.allConnections.set([...this.allConnections(), connection]);
          this.savingTicketProvider.set(false);
          this.showTicketForm.set(false);
          this.ticketForm = this.newTicketForm();
          this.ticketSaveSuccess.set(true);
          setTimeout(() => this.ticketSaveSuccess.set(false), 3000);
        },
        error: (err: any) => {
          const msg = err?.error?.message || this.translate.instant('projectConfig.ticketProviders.errors.saveFailed');
          this.ticketSaveError.set(msg);
          this.savingTicketProvider.set(false);
        },
      });
    }
  }

  deleteTicketProvider(id: string): void {
    this.deletingConnectionId.set(id);
    this.platformService.deleteConnection(id).subscribe({
      next: () => {
        this.ticketProviders.set(this.ticketProviders().filter((c) => c.id !== id));
        this.allConnections.set(this.allConnections().filter((c) => c.id !== id));
        this.deletingConnectionId.set(null);
      },
      error: () => {
        this.deletingConnectionId.set(null);
      },
    });
  }

  // ===== Test Connection (shared by Steps 1 & 2) =====

  testConnection(conn: PlatformConnection): void {
    this.testingConnectionId.set(conn.id);
    this.testConnectionResult.set(null);

    this.platformService.testConnection(conn.id).subscribe({
      next: (success) => {
        this.testingConnectionId.set(null);
        this.testConnectionResult.set({ id: conn.id, success });
        // Update the connection status in-place
        const status = success ? ConnectionStatus.ACTIVE : ConnectionStatus.ERROR;
        this.ticketProviders.set(this.ticketProviders().map((c) => c.id === conn.id ? { ...c, status } : c));
        this.gitRemotes.set(this.gitRemotes().map((c) => c.id === conn.id ? { ...c, status } : c));
        this.allConnections.set(this.allConnections().map((c) => c.id === conn.id ? { ...c, status } : c));
        setTimeout(() => this.testConnectionResult.set(null), 5000);
      },
      error: () => {
        this.testingConnectionId.set(null);
        this.testConnectionResult.set({ id: conn.id, success: false });
        // Update the connection status to ERROR
        this.ticketProviders.set(this.ticketProviders().map((c) => c.id === conn.id ? { ...c, status: ConnectionStatus.ERROR } : c));
        this.gitRemotes.set(this.gitRemotes().map((c) => c.id === conn.id ? { ...c, status: ConnectionStatus.ERROR } : c));
        this.allConnections.set(this.allConnections().map((c) => c.id === conn.id ? { ...c, status: ConnectionStatus.ERROR } : c));
        setTimeout(() => this.testConnectionResult.set(null), 5000);
      },
    });
  }

  // ===== STEP 2: Git Remotes =====

  getGitAuthTypeOptions(): { value: string; label: string; fields: { key: string; label: string; secret: boolean }[] }[] {
    return AUTH_TYPE_OPTIONS[this.gitForm.platformType] ?? [];
  }

  getGitAuthFields(): { key: string; label: string; secret: boolean }[] {
    const options = this.getGitAuthTypeOptions();
    const selected = options.find((o) => o.value === this.gitForm.authType);
    return selected?.fields ?? [];
  }

  onGitPlatformTypeChange(): void {
    const options = this.getGitAuthTypeOptions();
    this.gitForm.authType = options.length > 0 ? options[0].value : '';
    this.gitForm.credentials = {};
    if (this.isCloudPlatform(this.gitForm.platformType)) {
      this.gitForm.baseUrl = this.getDefaultBaseUrl(this.gitForm.platformType);
    } else {
      this.gitForm.baseUrl = '';
    }
  }

  onGitAuthTypeChange(): void {
    this.gitForm.credentials = {};
  }

  toggleGitForm(): void {
    this.showGitForm.set(!this.showGitForm());
    if (!this.showGitForm()) {
      this.gitForm = this.newGitForm();
      this.gitSaveError.set(null);
      this.editingGitRemoteId.set(null);
    }
  }

  editGitRemote(conn: PlatformConnection): void {
    this.editingGitRemoteId.set(conn.id);
    this.gitForm = {
      name: conn.name,
      platformType: conn.platformType,
      baseUrl: conn.baseUrl || '',
      authType: this.getFirstAuthType(conn.platformType, conn.authType),
      credentials: {},
    };
    this.showGitForm.set(true);
    this.gitSaveError.set(null);
  }

  canSaveGitRemote(): boolean {
    const f = this.gitForm;
    if (!f.name.trim() || !f.platformType || !f.authType) return false;
    if (!this.isCloudPlatform(f.platformType) && !f.baseUrl.trim()) return false;
    // When editing, credentials are optional (only sent if user fills them in)
    if (this.editingGitRemoteId()) return true;
    const fields = this.getGitAuthFields();
    return fields.every((field) => (f.credentials[field.key] ?? '').trim().length > 0);
  }

  saveGitRemote(): void {
    const user = this.authService.user();
    if (!user) return;

    this.savingGitRemote.set(true);
    this.gitSaveError.set(null);

    const editingId = this.editingGitRemoteId();

    if (editingId) {
      // Update existing connection
      const payload: Partial<PlatformConnection> = {
        name: this.gitForm.name.trim(),
        platformType: this.gitForm.platformType as any,
        baseUrl: this.gitForm.baseUrl.trim() || this.getDefaultBaseUrl(this.gitForm.platformType),
        authType: this.gitForm.authType,
      };

      // Only include credentials if user actually filled them in
      const filledCredentials: Record<string, string> = {};
      let hasCredentials = false;
      for (const [key, value] of Object.entries(this.gitForm.credentials)) {
        if (value && value.trim()) {
          filledCredentials[key] = value.trim();
          hasCredentials = true;
        }
      }
      if (hasCredentials) {
        (payload as any).credentials = filledCredentials;
      }

      this.platformService.updateConnection(editingId, payload).subscribe({
        next: (updated) => {
          this.gitRemotes.set(this.gitRemotes().map((c) => c.id === updated.id ? updated : c));
          this.allConnections.set(this.allConnections().map((c) => c.id === updated.id ? updated : c));
          this.savingGitRemote.set(false);
          this.showGitForm.set(false);
          this.gitForm = this.newGitForm();
          this.editingGitRemoteId.set(null);
          this.gitSaveSuccess.set(true);
          setTimeout(() => this.gitSaveSuccess.set(false), 3000);
        },
        error: (err: any) => {
          const msg = err?.error?.message || this.translate.instant('projectConfig.gitRemotes.errors.saveFailed');
          this.gitSaveError.set(msg);
          this.savingGitRemote.set(false);
        },
      });
    } else {
      // Create new connection
      const request: CreateConnectionRequest = {
        tenantId: user.tenantId,
        name: this.gitForm.name.trim(),
        platformType: this.gitForm.platformType,
        baseUrl: this.gitForm.baseUrl.trim() || this.getDefaultBaseUrl(this.gitForm.platformType),
        authType: this.gitForm.authType,
        credentials: { ...this.gitForm.credentials },
      };

      this.platformService.createConnectionFromRequest(request).subscribe({
        next: (connection) => {
          this.gitRemotes.set([...this.gitRemotes(), connection]);
          this.allConnections.set([...this.allConnections(), connection]);
          this.savingGitRemote.set(false);
          this.showGitForm.set(false);
          this.gitForm = this.newGitForm();
          this.gitSaveSuccess.set(true);
          setTimeout(() => this.gitSaveSuccess.set(false), 3000);
        },
        error: (err: any) => {
          const msg = err?.error?.message || this.translate.instant('projectConfig.gitRemotes.errors.saveFailed');
          this.gitSaveError.set(msg);
          this.savingGitRemote.set(false);
        },
      });
    }
  }

  deleteGitRemote(id: string): void {
    this.deletingConnectionId.set(id);
    this.platformService.deleteConnection(id).subscribe({
      next: () => {
        this.gitRemotes.set(this.gitRemotes().filter((c) => c.id !== id));
        this.allConnections.set(this.allConnections().filter((c) => c.id !== id));
        this.sshKeys.set(this.sshKeys().filter((k) => k.connectionId !== id));
        this.deletingConnectionId.set(null);
      },
      error: () => {
        this.deletingConnectionId.set(null);
      },
    });
  }

  // --- SSH Key methods ---

  getSshKeysForConnection(connectionId: string): SshKey[] {
    return this.sshKeys().filter((k) => k.connectionId === connectionId);
  }

  toggleSshKeyForm(): void {
    this.showSshKeyForm.set(!this.showSshKeyForm());
    if (!this.showSshKeyForm()) {
      this.sshKeyForm = this.newSshKeyForm();
      this.sshKeySaveError.set(null);
    } else {
      // Auto-select the only git remote if there's exactly one
      const remotes = this.gitRemotes();
      if (remotes.length === 1) {
        this.sshKeyForm.connectionId = remotes[0].id;
      }
    }
  }

  canSaveSshKey(): boolean {
    const f = this.sshKeyForm;
    return f.connectionId.length > 0 && f.name.trim().length > 0 &&
      f.publicKey.trim().length > 0 && f.privateKey.trim().length > 0;
  }

  saveSshKey(): void {
    const user = this.authService.user();
    if (!user) return;

    this.savingSshKey.set(true);
    this.sshKeySaveError.set(null);

    const request: CreateSshKeyRequest = {
      tenantId: user.tenantId,
      connectionId: this.sshKeyForm.connectionId,
      name: this.sshKeyForm.name.trim(),
      publicKey: this.sshKeyForm.publicKey.trim(),
      privateKey: this.sshKeyForm.privateKey.trim(),
      keyType: this.sshKeyForm.keyType || undefined,
      keyUsage: this.sshKeyForm.keyUsage || undefined,
    };

    this.sshKeyService.createSshKey(request).subscribe({
      next: (key) => {
        this.sshKeys.set([...this.sshKeys(), key]);
        this.savingSshKey.set(false);
        this.showSshKeyForm.set(false);
        this.sshKeyForm = this.newSshKeyForm();
        this.sshKeySaveSuccess.set(true);
        setTimeout(() => this.sshKeySaveSuccess.set(false), 3000);
      },
      error: (err: any) => {
        const msg = err?.error?.message || this.translate.instant('projectConfig.sshKeys.errors.saveFailed');
        this.sshKeySaveError.set(msg);
        this.savingSshKey.set(false);
      },
    });
  }

  deleteSshKey(id: string): void {
    this.deletingSshKeyId.set(id);
    this.sshKeyService.deleteSshKey(id).subscribe({
      next: () => {
        this.sshKeys.set(this.sshKeys().filter((k) => k.id !== id));
        this.deletingSshKeyId.set(null);
      },
      error: () => {
        this.deletingSshKeyId.set(null);
      },
    });
  }

  generateDeployKey(connectionId: string): void {
    const user = this.authService.user();
    if (!user || !connectionId) return;

    this.generatingDeployKey.set(true);
    this.generatedPublicKey.set(null);
    this.sshKeySaveError.set(null);

    const conn = this.allConnections().find((c) => c.id === connectionId);
    const name = conn ? `deploy-key-${conn.name.toLowerCase().replace(/[^a-z0-9]/g, '-')}` : 'deploy-key';

    this.sshKeyService.generateDeployKey(connectionId, user.tenantId, name).subscribe({
      next: (key) => {
        this.sshKeys.set([...this.sshKeys(), key]);
        this.generatedPublicKey.set(key.publicKey);
        this.generatingDeployKey.set(false);
      },
      error: (err: any) => {
        const msg = err?.error?.message || this.translate.instant('projectConfig.sshKeys.errors.generateFailed');
        this.sshKeySaveError.set(msg);
        this.generatingDeployKey.set(false);
      },
    });
  }

  dismissGeneratedKey(): void {
    this.generatedPublicKey.set(null);
    this.copiedKey.set(false);
  }

  copyGeneratedKey(): void {
    const key = this.generatedPublicKey();
    if (!key) return;
    navigator.clipboard.writeText(key).then(() => {
      this.copiedKey.set(true);
      setTimeout(() => this.copiedKey.set(false), 3000);
    });
  }

  getKeyUsageLabel(keyUsage?: string): string {
    if (keyUsage === 'DEPLOY_KEY') return this.translate.instant('projectConfig.sshKeys.keyUsage.deployKey');
    return this.translate.instant('projectConfig.sshKeys.keyUsage.userKey');
  }

  /** Returns credential status for a connection: LINKED (has keys/active), EXPIRED, or MISSING. */
  getCredentialStatus(connectionId: string | undefined): string {
    if (!connectionId) return 'MISSING';
    const conn = this.allConnections().find((c) => c.id === connectionId);
    if (!conn) return 'MISSING';
    if (conn.status === 'ERROR') return 'EXPIRED';
    // Check if connection has SSH keys or is ACTIVE (implying valid credentials)
    const hasKeys = this.sshKeys().some((k) => k.connectionId === connectionId);
    if (conn.status === 'ACTIVE' || hasKeys) return 'LINKED';
    return 'MISSING';
  }

  getCredentialStatusLabel(status: string): string {
    const keyMap: Record<string, string> = {
      'LINKED': 'projectConfig.credentialStatus.linked',
      'EXPIRED': 'projectConfig.credentialStatus.expired',
      'MISSING': 'projectConfig.credentialStatus.missing',
    };
    const key = keyMap[status];
    return key ? this.translate.instant(key) : status;
  }

  // ===== Review Bot Configuration =====

  getReviewBotForConnection(connectionId: string): ReviewBotConfig | undefined {
    return this.reviewBotConfigs().find((c) => c.connectionId === connectionId);
  }

  toggleReviewBotForm(): void {
    this.showReviewBotForm.set(!this.showReviewBotForm());
    if (!this.showReviewBotForm()) {
      this.reviewBotForm = this.newReviewBotForm();
      this.reviewBotSaveError.set(null);
    } else {
      const remotes = this.gitRemotes();
      if (remotes.length === 1) {
        this.reviewBotForm.connectionId = remotes[0].id;
      }
    }
  }

  canSaveReviewBot(): boolean {
    const f = this.reviewBotForm;
    return f.connectionId.length > 0 && f.botUsername.trim().length > 0 && f.botAccessToken.trim().length > 0;
  }

  saveReviewBot(): void {
    const user = this.authService.user();
    if (!user) return;

    this.savingReviewBot.set(true);
    this.reviewBotSaveError.set(null);

    const request: CreateReviewBotConfigRequest = {
      tenantId: user.tenantId,
      connectionId: this.reviewBotForm.connectionId,
      botUsername: this.reviewBotForm.botUsername.trim(),
      botAccessToken: this.reviewBotForm.botAccessToken.trim(),
      enabled: this.reviewBotForm.enabled,
      autoAssign: this.reviewBotForm.autoAssign,
    };

    this.reviewBotConfigService.createConfig(request).subscribe({
      next: (config) => {
        this.reviewBotConfigs.set([...this.reviewBotConfigs(), config]);
        this.savingReviewBot.set(false);
        this.showReviewBotForm.set(false);
        this.reviewBotForm = this.newReviewBotForm();
        this.reviewBotSaveSuccess.set(true);
        setTimeout(() => this.reviewBotSaveSuccess.set(false), 3000);
      },
      error: (err: any) => {
        const msg = err?.error?.message || this.translate.instant('projectConfig.reviewBot.errors.saveFailed');
        this.reviewBotSaveError.set(msg);
        this.savingReviewBot.set(false);
      },
    });
  }

  toggleReviewBotEnabled(config: ReviewBotConfig): void {
    this.reviewBotConfigService.updateConfig(config.id, { enabled: !config.enabled }).subscribe({
      next: (updated) => {
        this.reviewBotConfigs.set(this.reviewBotConfigs().map((c) => c.id === updated.id ? updated : c));
      },
    });
  }

  deleteReviewBot(id: string): void {
    this.deletingReviewBotId.set(id);
    this.reviewBotConfigService.deleteConfig(id).subscribe({
      next: () => {
        this.reviewBotConfigs.set(this.reviewBotConfigs().filter((c) => c.id !== id));
        this.deletingReviewBotId.set(null);
      },
      error: () => {
        this.deletingReviewBotId.set(null);
      },
    });
  }

  // ===== STEP 3: Projects =====

  toggleImportPanel(): void {
    this.showImportPanel.set(!this.showImportPanel());
    if (!this.showImportPanel()) {
      this.resetImportState();
    } else {
      const conns = this.ticketProviders();
      if (conns.length === 1) {
        this.importConnectionId.set(conns[0].id);
        this.fetchRemoteProjects();
      }
    }
  }

  onImportConnectionChange(connectionId: string): void {
    this.importConnectionId.set(connectionId);
    this.importCandidates.set([]);
    this.importError.set(null);
    this.importSaveError.set(null);
    this.importFetchComplete.set(false);
    if (connectionId) {
      this.fetchRemoteProjects();
    }
  }

  fetchRemoteProjects(): void {
    const connectionId = this.importConnectionId();
    if (!connectionId) return;

    this.importLoading.set(true);
    this.importError.set(null);
    this.importCandidates.set([]);
    this.importFetchComplete.set(false);

    this.platformService.getRemoteProjects(connectionId).subscribe({
      next: (remoteProjects) => {
        const existingKeys = new Set(
          this.projectStates()
            .map((ps) => ps.project.externalProjectId)
            .filter(Boolean),
        );
        const candidates: ImportCandidate[] = remoteProjects.map((rp) => ({
          remote: rp,
          selected: false,
          name: rp.name,
          description: rp.description ?? '',
        }));
        candidates.forEach((c) => {
          if (existingKeys.has(c.remote.key)) {
            (c as any)._alreadyImported = true;
          }
        });
        this.importCandidates.set(candidates);
        this.importFetchComplete.set(true);
        this.importLoading.set(false);
      },
      error: (err: any) => {
        const msg = err?.error?.message || err?.message || this.translate.instant('projectConfig.projects.errors.fetchFailed');
        this.importError.set(msg);
        this.importFetchComplete.set(true);
        this.importLoading.set(false);
      },
    });
  }

  toggleCandidateSelection(index: number): void {
    const candidates = [...this.importCandidates()];
    candidates[index] = { ...candidates[index], selected: !candidates[index].selected };
    this.importCandidates.set(candidates);
  }

  selectAllCandidates(): void {
    const candidates = this.importCandidates().map((c) => ({
      ...c,
      selected: !this.isAlreadyImported(c),
    }));
    this.importCandidates.set(candidates);
  }

  deselectAllCandidates(): void {
    const candidates = this.importCandidates().map((c) => ({ ...c, selected: false }));
    this.importCandidates.set(candidates);
  }

  updateCandidateName(index: number, value: string): void {
    const candidates = [...this.importCandidates()];
    candidates[index] = { ...candidates[index], name: value };
    this.importCandidates.set(candidates);
  }

  updateCandidateDescription(index: number, value: string): void {
    const candidates = [...this.importCandidates()];
    candidates[index] = { ...candidates[index], description: value };
    this.importCandidates.set(candidates);
  }

  getSelectedCandidates(): ImportCandidate[] {
    return this.importCandidates().filter((c) => c.selected);
  }

  isAlreadyImported(candidate: ImportCandidate): boolean {
    return !!(candidate as any)._alreadyImported;
  }

  canImport(): boolean {
    return this.getSelectedCandidates().length > 0 && !this.importSaving();
  }

  importSelected(): void {
    const selected = this.getSelectedCandidates();
    if (selected.length === 0) return;

    this.importSaving.set(true);
    this.importSaveError.set(null);
    this.importProgress.set({ done: 0, total: selected.length });

    let completed = 0;
    let errors = 0;

    selected.forEach((candidate) => {
      const project: Partial<Project> = {
        name: candidate.name.trim(),
        description: candidate.description.trim() || undefined,
        defaultBranch: 'main',
        connectionId: this.importConnectionId(),
        externalProjectId: candidate.remote.key,
        branchNamingTemplate: '{strategy}/{ticket}-{description}',
      };

      this.projectService.createProject(project).subscribe({
        next: (created) => {
          completed++;
          this.importProgress.set({ done: completed + errors, total: selected.length });
          const newState: ProjectMappingState = {
            project: created,
            expanded: false,
            mappings: [],
            mappingsLoaded: true,
            remoteStatuses: [],
            saving: false,
            saveSuccess: false,
            saveError: null,
            loading: false,
            fetchingStatuses: false,
            fetchError: null,
            connectionName: this.getConnectionName(created.connectionId, this.allConnections()),
          };
          this.projectStates.set([...this.projectStates(), newState]);

          if (completed + errors === selected.length) {
            this.finishImport(errors);
          }
        },
        error: () => {
          errors++;
          this.importProgress.set({ done: completed + errors, total: selected.length });
          if (completed + errors === selected.length) {
            this.finishImport(errors);
          }
        },
      });
    });
  }

  // --- Project Edit/Remove ---

  toggleProjectEditForm(): void {
    this.showProjectEditForm.set(!this.showProjectEditForm());
    if (!this.showProjectEditForm()) {
      this.projectEditForm = this.newProjectEditForm();
      this.projectSaveError.set(null);
      this.editingProjectId.set(null);
    }
  }

  editProject(ps: ProjectMappingState): void {
    this.editingProjectId.set(ps.project.id);
    this.projectEditForm = {
      name: ps.project.name,
      description: ps.project.description || '',
      connectionId: ps.project.connectionId || '',
    };
    this.showProjectEditForm.set(true);
    this.projectSaveError.set(null);
  }

  canSaveProject(): boolean {
    const f = this.projectEditForm;
    return f.name.trim().length > 0;
  }

  saveProject(): void {
    const editingId = this.editingProjectId();
    if (!editingId) return;

    this.savingProject.set(true);
    this.projectSaveError.set(null);

    const payload: Partial<Project> = {
      name: this.projectEditForm.name.trim(),
      description: this.projectEditForm.description.trim() || undefined,
      connectionId: this.projectEditForm.connectionId || undefined,
    };

    this.projectService.updateProject(editingId, payload).subscribe({
      next: (updated) => {
        this.projectStates.set(
          this.projectStates().map((ps) =>
            ps.project.id === editingId
              ? {
                  ...ps,
                  project: updated,
                  connectionName: this.getConnectionName(updated.connectionId, this.allConnections()),
                }
              : ps,
          ),
        );
        this.savingProject.set(false);
        this.showProjectEditForm.set(false);
        this.projectEditForm = this.newProjectEditForm();
        this.editingProjectId.set(null);
        this.projectSaveSuccess.set(true);
        setTimeout(() => this.projectSaveSuccess.set(false), 3000);
      },
      error: (err: any) => {
        const msg = err?.error?.message || this.translate.instant('projectConfig.projects.errors.saveFailed');
        this.projectSaveError.set(msg);
        this.savingProject.set(false);
      },
    });
  }

  deleteProject(id: string): void {
    this.deletingProjectId.set(id);
    this.projectService.deleteProject(id).subscribe({
      next: () => {
        this.projectStates.set(this.projectStates().filter((ps) => ps.project.id !== id));
        this.deletingProjectId.set(null);
      },
      error: () => {
        this.deletingProjectId.set(null);
      },
    });
  }

  // ===== STEP 4: Branch & Workflow =====

  toggleProject(index: number): void {
    const states = [...this.projectStates()];
    const state = { ...states[index] };
    state.expanded = !state.expanded;

    if (state.expanded && !state.mappingsLoaded && !state.loading) {
      state.loading = true;
      states[index] = state;
      this.projectStates.set(states);

      this.projectService.getWorkflowMappings(state.project.id).subscribe({
        next: (mappings) => {
          const updated = [...this.projectStates()];
          updated[index] = { ...updated[index], mappings, loading: false, mappingsLoaded: true };
          this.projectStates.set(updated);
        },
        error: () => {
          const updated = [...this.projectStates()];
          updated[index] = { ...updated[index], mappings: [], loading: false, mappingsLoaded: true };
          this.projectStates.set(updated);
        },
      });
    } else {
      states[index] = state;
      this.projectStates.set(states);
    }
  }

  fetchRemoteStatuses(index: number): void {
    const states = [...this.projectStates()];
    const state = { ...states[index] };
    const project = state.project;

    if (!project.connectionId || !project.externalProjectId) {
      state.fetchError = this.translate.instant('projectConfig.branchWorkflow.errors.noProviderLinked');
      states[index] = state;
      this.projectStates.set(states);
      setTimeout(() => {
        const current = [...this.projectStates()];
        current[index] = { ...current[index], fetchError: null };
        this.projectStates.set(current);
      }, 5000);
      return;
    }

    state.fetchingStatuses = true;
    state.fetchError = null;
    states[index] = state;
    this.projectStates.set(states);

    this.platformService.getProjectStatuses(project.connectionId, project.externalProjectId).subscribe({
      next: (statuses) => {
        const updated = [...this.projectStates()];
        updated[index] = {
          ...updated[index],
          remoteStatuses: statuses,
          fetchingStatuses: false,
          fetchError: null,
        };
        this.projectStates.set(updated);
      },
      error: () => {
        console.error('Failed to fetch remote statuses');
        const updated = [...this.projectStates()];
        updated[index] = {
          ...updated[index],
          remoteStatuses: [],
          fetchingStatuses: false,
          fetchError: this.translate.instant('projectConfig.branchWorkflow.errors.fetchStatusesFailed'),
        };
        this.projectStates.set(updated);
      },
    });
  }

  updateProjectConnection(index: number, connectionId: string): void {
    const states = [...this.projectStates()];
    const state = { ...states[index] };
    state.project = { ...state.project, connectionId: connectionId || undefined };
    state.connectionName = this.getConnectionName(connectionId, this.allConnections());
    state.remoteStatuses = [];
    states[index] = state;
    this.projectStates.set(states);
  }

  updateExternalProjectId(index: number, value: string): void {
    const states = [...this.projectStates()];
    const state = { ...states[index] };
    state.project = { ...state.project, externalProjectId: value || undefined };
    state.remoteStatuses = [];
    states[index] = state;
    this.projectStates.set(states);
  }

  updateBranchNamingTemplate(index: number, value: string): void {
    const states = [...this.projectStates()];
    const state = { ...states[index] };
    state.project = { ...state.project, branchNamingTemplate: value };
    states[index] = state;
    this.projectStates.set(states);
  }

  updateDefaultBranch(index: number, value: string): void {
    const states = [...this.projectStates()];
    const state = { ...states[index] };
    state.project = { ...state.project, defaultBranch: value || 'main' };
    states[index] = state;
    this.projectStates.set(states);
  }

  updateGitConnectionId(index: number, connectionId: string): void {
    const states = [...this.projectStates()];
    const state = { ...states[index] };
    state.project = { ...state.project, gitConnectionId: connectionId || undefined };
    states[index] = state;
    this.projectStates.set(states);
  }

  updateCloneUrl(index: number, value: string): void {
    const states = [...this.projectStates()];
    const state = { ...states[index] };
    state.project = { ...state.project, cloneUrl: value || undefined };
    states[index] = state;
    this.projectStates.set(states);
  }

  updateRepositoryUrl(index: number, value: string): void {
    const states = [...this.projectStates()];
    const state = { ...states[index] };
    state.project = { ...state.project, repositoryUrl: value || undefined };
    states[index] = state;
    this.projectStates.set(states);
  }

  testGitAccess(index: number): void {
    const state = this.projectStates()[index];
    const project = state.project;

    if (!project.cloneUrl) return;

    this.testingGitAccessProjectId.set(project.id);
    this.testGitAccessResult.set(null);
    this.gitAccessStepsExpanded.set(false);

    // Initialize progress steps
    const isSsh = project.cloneUrl.startsWith('git@') || project.cloneUrl.startsWith('ssh://');
    const steps: GitAccessStep[] = [
      { label: this.translate.instant('projectConfig.branchWorkflow.gitAccessSteps.resolvingCredentials'), status: 'running', startedAt: Date.now() },
      { label: this.translate.instant('projectConfig.branchWorkflow.gitAccessSteps.provisioningContainer'), status: 'pending' },
      { label: isSsh
          ? this.translate.instant('projectConfig.branchWorkflow.gitAccessSteps.configuringSshKey')
          : this.translate.instant('projectConfig.branchWorkflow.gitAccessSteps.configuringHttpsToken'),
        status: 'pending' },
      { label: this.translate.instant('projectConfig.branchWorkflow.gitAccessSteps.runningGitLsRemote'), status: 'pending' },
      { label: this.translate.instant('projectConfig.branchWorkflow.gitAccessSteps.cleaningUp'), status: 'pending' },
    ];
    this.gitAccessSteps.set({ projectId: project.id, steps: [...steps] });

    // Resolve credentials from the git connection
    const gitConn = project.gitConnectionId
      ? this.allConnections().find((c) => c.id === project.gitConnectionId)
      : null;

    // Look for SSH keys associated with the git connection
    const sshKey = project.gitConnectionId
      ? this.sshKeys().find((k) => k.connectionId === project.gitConnectionId)
      : null;

    // Build request
    const request: { cloneUrl: string; accessToken?: string; sshKeyId?: string; branch?: string } = {
      cloneUrl: project.cloneUrl,
      branch: project.defaultBranch || 'main',
    };

    if (sshKey) {
      request.sshKeyId = sshKey.id;
    }

    // Mark step 1 done, step 2 running
    this.advanceGitAccessStep(project.id, steps, 0, 'done',
      sshKey ? this.translate.instant('projectConfig.branchWorkflow.gitAccessSteps.sshKeyResolved') : gitConn ? this.translate.instant('projectConfig.branchWorkflow.gitAccessSteps.connectionResolved') : this.translate.instant('projectConfig.branchWorkflow.gitAccessSteps.noCredentials'));

    // Simulate step 2 (container provisioning) start — actual provisioning happens server-side
    this.advanceGitAccessStep(project.id, steps, 1, 'running');

    // After a brief delay to show container step, advance to step 3
    setTimeout(() => {
      this.advanceGitAccessStep(project.id, steps, 1, 'done', 'squadron/workspace-base:latest');
      this.advanceGitAccessStep(project.id, steps, 2, 'done',
        isSsh ? this.translate.instant('projectConfig.branchWorkflow.gitAccessSteps.sshConfigured') : this.translate.instant('projectConfig.branchWorkflow.gitAccessSteps.httpsConfigured'));
      this.advanceGitAccessStep(project.id, steps, 3, 'running');
    }, 400);

    this.workspaceService.testGitAccess(request).subscribe({
      next: (result) => {
        // Mark git ls-remote done
        this.advanceGitAccessStep(project.id, steps, 3, 'done',
          result.branch ? `refs/heads/${result.branch}` : undefined);
        // Mark cleanup done
        this.advanceGitAccessStep(project.id, steps, 4, 'done',
          this.translate.instant('projectConfig.branchWorkflow.gitAccessSteps.containerDestroyed'));

        this.testingGitAccessProjectId.set(null);
        this.testGitAccessResult.set({ projectId: project.id, result });
        setTimeout(() => {
          this.testGitAccessResult.set(null);
          this.gitAccessSteps.set(null);
        }, 15000);
      },
      error: (err: any) => {
        // Mark the current running step as error
        const currentRunning = steps.findIndex(s => s.status === 'running');
        if (currentRunning >= 0) {
          this.advanceGitAccessStep(project.id, steps, currentRunning, 'error',
            err?.error?.message || err?.message || this.translate.instant('projectConfig.branchWorkflow.gitConfig.testFailed'));
        }
        // Mark cleanup as done even on failure
        if (steps[4].status === 'pending') {
          this.advanceGitAccessStep(project.id, steps, 4, 'done',
            this.translate.instant('projectConfig.branchWorkflow.gitAccessSteps.containerDestroyed'));
        }

        this.testingGitAccessProjectId.set(null);
        const message = err?.error?.message || err?.message || this.translate.instant('projectConfig.branchWorkflow.gitConfig.testFailed');
        this.testGitAccessResult.set({
          projectId: project.id,
          result: { success: false, message, durationMs: 0 },
        });
        setTimeout(() => {
          this.testGitAccessResult.set(null);
          this.gitAccessSteps.set(null);
        }, 15000);
      },
    });
  }

  toggleGitAccessSteps(): void {
    this.gitAccessStepsExpanded.set(!this.gitAccessStepsExpanded());
  }

  private advanceGitAccessStep(projectId: string, steps: GitAccessStep[], index: number, status: 'running' | 'done' | 'error', detail?: string): void {
    const step = steps[index];
    step.status = status;
    if (detail) step.detail = detail;
    if (status === 'running' && !step.startedAt) step.startedAt = Date.now();
    if (status === 'done' || status === 'error') step.completedAt = Date.now();
    this.gitAccessSteps.set({ projectId, steps: [...steps] });
  }

  getGitConnectionName(gitConnectionId: string | undefined): string | null {
    if (!gitConnectionId) return null;
    const conn = this.allConnections().find((c) => c.id === gitConnectionId);
    return conn ? conn.name : null;
  }

  addMapping(index: number): void {
    const states = [...this.projectStates()];
    const state = { ...states[index] };
    const usedStates = state.mappings.map((m) => m.internalState);
    const available = this.workflowStates().filter((s) => !usedStates.includes(s));
    if (available.length === 0) return;

    state.mappings = [...state.mappings, { internalState: available[0], externalStatus: '' }];
    states[index] = state;
    this.projectStates.set(states);
  }

  removeMapping(projectIndex: number, mappingIndex: number): void {
    const states = [...this.projectStates()];
    const state = { ...states[projectIndex] };
    state.mappings = state.mappings.filter((_, i) => i !== mappingIndex);
    states[projectIndex] = state;
    this.projectStates.set(states);
  }

  updateMappingState(projectIndex: number, mappingIndex: number, value: string): void {
    const states = [...this.projectStates()];
    const state = { ...states[projectIndex] };
    state.mappings = state.mappings.map((m, i) =>
      i === mappingIndex ? { ...m, internalState: value } : m,
    );
    states[projectIndex] = state;
    this.projectStates.set(states);
  }

  updateMappingStatus(projectIndex: number, mappingIndex: number, value: string): void {
    const states = [...this.projectStates()];
    const state = { ...states[projectIndex] };
    state.mappings = state.mappings.map((m, i) =>
      i === mappingIndex ? { ...m, externalStatus: value } : m,
    );
    states[projectIndex] = state;
    this.projectStates.set(states);
  }

  getAvailableStates(projectIndex: number, currentState: string): string[] {
    const state = this.projectStates()[projectIndex];
    const usedStates = state.mappings
      .map((m) => m.internalState)
      .filter((s) => s !== currentState);
    return this.workflowStates().filter((s) => !usedStates.includes(s));
  }

  saveMappings(index: number): void {
    const states = [...this.projectStates()];
    const state = { ...states[index] };

    const validMappings = state.mappings.filter(
      (m) => m.internalState && m.externalStatus.trim(),
    );

    state.saving = true;
    state.saveSuccess = false;
    state.saveError = null;
    states[index] = state;
    this.projectStates.set(states);

    // Save workflow mappings, branch naming template, default branch, git config, and connection info together
    forkJoin({
      mappings: this.projectService.saveWorkflowMappings(state.project.id, validMappings),
      project: this.projectService.updateProject(state.project.id, {
        branchNamingTemplate: state.project.branchNamingTemplate || '{strategy}/{ticket}-{description}',
        defaultBranch: state.project.defaultBranch || 'main',
        connectionId: state.project.connectionId,
        externalProjectId: state.project.externalProjectId,
        gitConnectionId: state.project.gitConnectionId,
        cloneUrl: state.project.cloneUrl,
        repositoryUrl: state.project.repositoryUrl,
      }),
    }).subscribe({
      next: ({ mappings, project }) => {
        const updated = [...this.projectStates()];
        const current = updated[index];
        updated[index] = {
          ...current,
          mappings,
          project: { ...project, connectionId: project.connectionId, externalProjectId: project.externalProjectId },
          saving: false,
          saveSuccess: true,
          saveError: null,
          mappingsLoaded: true,
        };
        this.projectStates.set(updated);
        setTimeout(() => {
          const current = [...this.projectStates()];
          current[index] = { ...current[index], saveSuccess: false };
          this.projectStates.set(current);
        }, 3000);
      },
      error: (err: any) => {
        const msg = err?.error?.message || this.translate.instant('projectConfig.branchWorkflow.errors.saveMappingsFailed');
        const updated = [...this.projectStates()];
        updated[index] = {
          ...updated[index],
          saving: false,
          saveError: msg,
        };
        this.projectStates.set(updated);
        setTimeout(() => {
          const current = [...this.projectStates()];
          current[index] = { ...current[index], saveError: null };
          this.projectStates.set(current);
        }, 5000);
      },
    });
  }

  formatState(state: string): string {
    return state
      .replace(/_/g, ' ')
      .split(' ')
      .map((word) => word.length <= 2 ? word.toUpperCase() : word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }

  canFetchStatuses(ps: ProjectMappingState): boolean {
    return !!ps.project.connectionId && !!ps.project.externalProjectId && !ps.fetchingStatuses;
  }

  getConnectionPlatformType(connectionId: string | undefined): string | null {
    if (!connectionId) return null;
    const conn = this.allConnections().find((c) => c.id === connectionId);
    return conn ? conn.platformType : null;
  }

  getConnectionStatus(connectionId: string | undefined): string | null {
    if (!connectionId) return null;
    const conn = this.allConnections().find((c) => c.id === connectionId);
    return conn ? conn.status : null;
  }

  getMappingLabel(ps: ProjectMappingState): string {
    if (!ps.mappingsLoaded) {
      return this.translate.instant('projectConfig.branchWorkflow.loadingMappings');
    }
    if (ps.mappings.length > 0) {
      return this.translate.instant('projectConfig.branchWorkflow.mappingCount', { count: ps.mappings.length });
    }
    return this.translate.instant('projectConfig.branchWorkflow.notConfigured');
  }

  // --- Private helpers ---

  /**
   * Given a platformType and a stored authType string (which may be a raw backend value
    * like "PAT" or "API Token"), returns the matching AUTH_TYPE_OPTIONS value for that
    * platform, falling back to the first available auth type.
    */
   private getFirstAuthType(platformType: string, storedAuthType?: string): string {
    const options = AUTH_TYPE_OPTIONS[platformType] ?? [];
    if (options.length === 0) return '';
    if (storedAuthType) {
      // Direct match by value (e.g. "PAT", "API Token")
      const direct = options.find((o) => o.value === storedAuthType);
      if (direct) return direct.value;
      // Legacy match: stored value may be an i18n key from before the fix
      const legacy = options.find((o) => o.label === storedAuthType);
      if (legacy) return legacy.value;
      // Match by normalized key (e.g. "API_TOKEN" -> "apitoken" matches "API Token" -> "apitoken")
      const normalized = storedAuthType.toLowerCase().replace(/[_ ]/g, '');
      const byKey = options.find((o) => {
        return o.value.toLowerCase().replace(/[_ ]/g, '') === normalized;
      });
      if (byKey) return byKey.value;
    }
    return options[0].value;
  }

  private categorizeConnections(connections: PlatformConnection[]): void {
    const tickets: PlatformConnection[] = [];
    const remotes: PlatformConnection[] = [];
    connections.forEach((c) => {
      if (c.platformCategory === 'GIT_REMOTE' || GIT_REMOTE_TYPES.has(c.platformType)) {
        remotes.push(c);
      } else {
        tickets.push(c);
      }
    });
    this.ticketProviders.set(tickets);
    this.gitRemotes.set(remotes);
  }

  private finishImport(errors: number): void {
    this.importSaving.set(false);
    if (errors > 0) {
      this.importSaveError.set(this.translate.instant('projectConfig.projects.errors.partialImportFailure', { errors }));
    } else {
      this.showImportPanel.set(false);
      this.resetImportState();
    }
  }

  private resetImportState(): void {
    this.importConnectionId.set('');
    this.importCandidates.set([]);
    this.importLoading.set(false);
    this.importError.set(null);
    this.importSaveError.set(null);
    this.importProgress.set(null);
    this.importFetchComplete.set(false);
  }

  private getConnectionName(connectionId: string | undefined, connections: PlatformConnection[]): string | null {
    if (!connectionId) return null;
    const conn = connections.find((c) => c.id === connectionId);
    return conn ? conn.name : null;
  }

  private newTicketForm(): ProviderForm {
    return { name: '', platformType: 'JIRA_CLOUD', baseUrl: '', authType: 'API Token', credentials: {} };
  }

  private newGitForm(): ProviderForm {
    return { name: '', platformType: 'GITHUB', baseUrl: 'https://api.github.com', authType: 'PAT', credentials: {} };
  }

  private newSshKeyForm(): SshKeyForm {
    return { connectionId: '', name: '', publicKey: '', privateKey: '', keyType: 'ED25519', keyUsage: 'USER_KEY' };
  }

  private newReviewBotForm(): { connectionId: string; botUsername: string; botAccessToken: string; enabled: boolean; autoAssign: boolean } {
    return { connectionId: '', botUsername: '', botAccessToken: '', enabled: true, autoAssign: true };
  }

  private newProjectEditForm(): ProjectEditForm {
    return { name: '', description: '', connectionId: '' };
  }
}
