import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProjectService } from '../../../core/services/project.service';
import { PlatformService } from '../../../core/services/platform.service';
import { SshKeyService } from '../../../core/services/ssh-key.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Project, RemoteProject, WorkflowMapping, BranchStrategyType } from '../../../core/models/project.model';
import {
  PlatformConnection,
  PlatformConnectionType,
  PlatformCategory,
  CreateConnectionRequest,
  SshKey,
  CreateSshKeyRequest,
} from '../../../core/models/security.model';
import { forkJoin } from 'rxjs';

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
}

interface ImportCandidate {
  remote: RemoteProject;
  selected: boolean;
  name: string;
  description: string;
  defaultBranch: string;
  repositoryUrl: string;
  branchNamingTemplate: string;
}

interface ProjectMappingState {
  project: Project;
  expanded: boolean;
  mappings: WorkflowMapping[];
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

const AUTH_TYPE_OPTIONS: Record<string, { label: string; fields: { key: string; label: string; secret: boolean }[] }[]> = {
  JIRA_CLOUD: [
    { label: 'API Token', fields: [{ key: 'email', label: 'Email', secret: false }, { key: 'apiToken', label: 'API Token', secret: true }] },
    { label: 'OAuth 2.0', fields: [{ key: 'clientId', label: 'Client ID', secret: false }, { key: 'clientSecret', label: 'Client Secret', secret: true }] },
  ],
  JIRA_SERVER: [
    { label: 'PAT', fields: [{ key: 'pat', label: 'Personal Access Token', secret: true }] },
    { label: 'Basic Auth', fields: [{ key: 'username', label: 'Username', secret: false }, { key: 'password', label: 'Password', secret: true }] },
  ],
  GITHUB: [
    { label: 'PAT', fields: [{ key: 'pat', label: 'Personal Access Token', secret: true }] },
    { label: 'App', fields: [{ key: 'appId', label: 'App ID', secret: false }, { key: 'privateKey', label: 'Private Key', secret: true }] },
  ],
  GITLAB: [
    { label: 'PAT', fields: [{ key: 'pat', label: 'Personal Access Token', secret: true }] },
  ],
  AZURE_DEVOPS: [
    { label: 'PAT', fields: [{ key: 'pat', label: 'Personal Access Token', secret: true }] },
  ],
  BITBUCKET: [
    { label: 'App Password', fields: [{ key: 'username', label: 'Username', secret: false }, { key: 'password', label: 'App Password', secret: true }] },
  ],
};

const BRANCH_STRATEGIES: { value: string; label: string; description: string }[] = [
  { value: 'TRUNK_BASED', label: 'Trunk-Based', description: 'All work flows to a single main branch' },
  { value: 'GITFLOW', label: 'Gitflow', description: 'Feature, develop, release, and hotfix branches' },
  { value: 'GITHUB_FLOW', label: 'GitHub Flow', description: 'Feature branches merged directly to main' },
  { value: 'GITLAB_FLOW', label: 'GitLab Flow', description: 'Feature branches with environment branches' },
  { value: 'RELEASE_BRANCHING', label: 'Release Branching', description: 'Separate branches for each release' },
];

const TICKET_PLATFORM_TYPES = ['JIRA_CLOUD', 'JIRA_SERVER', 'AZURE_DEVOPS'];
const GIT_PLATFORM_TYPES = ['GITHUB', 'GITLAB', 'BITBUCKET'];

@Component({
  selector: 'sq-project-config',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './project-config.component.html',
  styleUrl: './project-config.component.scss',
})
export class ProjectConfigComponent implements OnInit {
  private projectService = inject(ProjectService);
  private platformService = inject(PlatformService);
  private sshKeyService = inject(SshKeyService);
  private authService = inject(AuthService);

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
  ticketForm: ProviderForm = this.newTicketForm();

  // Step 2: Git Remotes
  gitRemotes = signal<PlatformConnection[]>([]);
  showGitForm = signal(false);
  savingGitRemote = signal(false);
  gitSaveError = signal<string | null>(null);
  gitSaveSuccess = signal(false);
  gitForm: ProviderForm = this.newGitForm();

  // SSH Keys
  sshKeys = signal<SshKey[]>([]);
  showSshKeyForm = signal(false);
  savingSshKey = signal(false);
  sshKeySaveError = signal<string | null>(null);
  sshKeySaveSuccess = signal(false);
  deletingSshKeyId = signal<string | null>(null);
  sshKeyForm: SshKeyForm = this.newSshKeyForm();

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

  // Step 4: Branch & Workflow (uses projectStates)

  readonly ticketPlatformTypes = TICKET_PLATFORM_TYPES;
  readonly gitPlatformTypes = GIT_PLATFORM_TYPES;
  readonly branchStrategies = BRANCH_STRATEGIES;

  readonly steps: { id: WizardStep; label: string; number: number }[] = [
    { id: 'ticket-providers', label: 'Ticket Providers', number: 1 },
    { id: 'git-remotes', label: 'Git Remotes', number: 2 },
    { id: 'projects', label: 'Projects', number: 3 },
    { id: 'branch-workflow', label: 'Branch & Workflow', number: 4 },
  ];

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.loadError.set(null);

    const user = this.authService.user();
    if (!user) {
      this.loadError.set('Not authenticated');
      this.loading.set(false);
      return;
    }

    forkJoin({
      projects: this.projectService.getProjectsByTenant(user.tenantId),
      states: this.projectService.getWorkflowStates(),
      connections: this.platformService.getConnectionsByTenant(user.tenantId),
      sshKeys: this.sshKeyService.getSshKeysByTenant(user.tenantId),
    }).subscribe({
      next: ({ projects, states, connections, sshKeys }) => {
        this.workflowStates.set(states);
        this.allConnections.set(connections);
        this.sshKeys.set(sshKeys);
        this.categorizeConnections(connections);
        this.projectStates.set(
          projects.map((p) => ({
            project: p,
            expanded: false,
            mappings: [],
            remoteStatuses: [],
            saving: false,
            saveSuccess: false,
            saveError: null,
            loading: false,
            fetchingStatuses: false,
            fetchError: null,
            connectionName: this.getConnectionName(p.connectionId, connections),
          })),
        );
        this.loading.set(false);
      },
      error: () => {
        console.error('Failed to load project configuration data');
        this.workflowStates.set([]);
        this.allConnections.set([]);
        this.ticketProviders.set([]);
        this.gitRemotes.set([]);
        this.sshKeys.set([]);
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
    switch (type) {
      case 'GITHUB': return 'GitHub';
      case 'GITLAB': return 'GitLab';
      case 'JIRA_CLOUD': return 'Jira Cloud';
      case 'JIRA_SERVER': return 'Jira Server / DC';
      case 'AZURE_DEVOPS': return 'Azure DevOps';
      case 'BITBUCKET': return 'Bitbucket';
      default: return type;
    }
  }

  platformDescription(type: string): string {
    switch (type) {
      case 'JIRA_CLOUD': return 'Atlassian-hosted Jira in the cloud';
      case 'JIRA_SERVER': return 'Self-hosted Jira Server or Data Center';
      case 'AZURE_DEVOPS': return 'Microsoft Azure DevOps Services';
      case 'GITHUB': return 'GitHub.com or GitHub Enterprise';
      case 'GITLAB': return 'GitLab.com or self-managed GitLab';
      case 'BITBUCKET': return 'Bitbucket Cloud or Bitbucket Server';
      default: return '';
    }
  }

  // ===== STEP 1: Ticket Providers =====

  getTicketAuthTypeOptions(): { label: string; fields: { key: string; label: string; secret: boolean }[] }[] {
    return AUTH_TYPE_OPTIONS[this.ticketForm.platformType] ?? [];
  }

  getTicketAuthFields(): { key: string; label: string; secret: boolean }[] {
    const options = this.getTicketAuthTypeOptions();
    const selected = options.find((o) => o.label === this.ticketForm.authType);
    return selected?.fields ?? [];
  }

  onTicketPlatformTypeChange(): void {
    const options = this.getTicketAuthTypeOptions();
    this.ticketForm.authType = options.length > 0 ? options[0].label : '';
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
    }
  }

  canSaveTicketProvider(): boolean {
    const f = this.ticketForm;
    if (!f.name.trim() || !f.platformType || !f.authType) return false;
    if (!this.isCloudPlatform(f.platformType) && !f.baseUrl.trim()) return false;
    const fields = this.getTicketAuthFields();
    return fields.every((field) => (f.credentials[field.key] ?? '').trim().length > 0);
  }

  saveTicketProvider(): void {
    const user = this.authService.user();
    if (!user) return;

    this.savingTicketProvider.set(true);
    this.ticketSaveError.set(null);

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
        const msg = err?.error?.message || 'Failed to save provider. Please check your configuration and try again.';
        this.ticketSaveError.set(msg);
        this.savingTicketProvider.set(false);
      },
    });
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

  // ===== STEP 2: Git Remotes =====

  getGitAuthTypeOptions(): { label: string; fields: { key: string; label: string; secret: boolean }[] }[] {
    return AUTH_TYPE_OPTIONS[this.gitForm.platformType] ?? [];
  }

  getGitAuthFields(): { key: string; label: string; secret: boolean }[] {
    const options = this.getGitAuthTypeOptions();
    const selected = options.find((o) => o.label === this.gitForm.authType);
    return selected?.fields ?? [];
  }

  onGitPlatformTypeChange(): void {
    const options = this.getGitAuthTypeOptions();
    this.gitForm.authType = options.length > 0 ? options[0].label : '';
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
    }
  }

  canSaveGitRemote(): boolean {
    const f = this.gitForm;
    if (!f.name.trim() || !f.platformType || !f.authType) return false;
    if (!this.isCloudPlatform(f.platformType) && !f.baseUrl.trim()) return false;
    const fields = this.getGitAuthFields();
    return fields.every((field) => (f.credentials[field.key] ?? '').trim().length > 0);
  }

  saveGitRemote(): void {
    const user = this.authService.user();
    if (!user) return;

    this.savingGitRemote.set(true);
    this.gitSaveError.set(null);

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
        const msg = err?.error?.message || 'Failed to save Git remote. Please check your configuration.';
        this.gitSaveError.set(msg);
        this.savingGitRemote.set(false);
      },
    });
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
        const msg = err?.error?.message || 'Failed to save SSH key. Please verify the key pair.';
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

  // ===== STEP 3: Projects =====

  toggleImportPanel(): void {
    this.showImportPanel.set(!this.showImportPanel());
    if (!this.showImportPanel()) {
      this.resetImportState();
    } else {
      const conns = this.allConnections();
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
          defaultBranch: 'main',
          repositoryUrl: rp.url ?? '',
          branchNamingTemplate: '{strategy}/{ticket}-{description}',
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
        const msg = err?.error?.message || err?.message || 'Failed to fetch projects. Please check the connection.';
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

  updateCandidateBranch(index: number, value: string): void {
    const candidates = [...this.importCandidates()];
    candidates[index] = { ...candidates[index], defaultBranch: value };
    this.importCandidates.set(candidates);
  }

  updateCandidateRepoUrl(index: number, value: string): void {
    const candidates = [...this.importCandidates()];
    candidates[index] = { ...candidates[index], repositoryUrl: value };
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
        defaultBranch: candidate.defaultBranch.trim() || 'main',
        repositoryUrl: candidate.repositoryUrl.trim() || undefined,
        connectionId: this.importConnectionId(),
        externalProjectId: candidate.remote.key,
        branchNamingTemplate: candidate.branchNamingTemplate || '{strategy}/{ticket}-{description}',
      };

      this.projectService.createProject(project).subscribe({
        next: (created) => {
          completed++;
          this.importProgress.set({ done: completed + errors, total: selected.length });
          const newState: ProjectMappingState = {
            project: created,
            expanded: false,
            mappings: [],
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

  // ===== STEP 4: Branch & Workflow =====

  toggleProject(index: number): void {
    const states = [...this.projectStates()];
    const state = { ...states[index] };
    state.expanded = !state.expanded;

    if (state.expanded && state.mappings.length === 0 && !state.loading) {
      state.loading = true;
      states[index] = state;
      this.projectStates.set(states);

      this.projectService.getWorkflowMappings(state.project.id).subscribe({
        next: (mappings) => {
          const updated = [...this.projectStates()];
          updated[index] = { ...updated[index], mappings, loading: false };
          this.projectStates.set(updated);
        },
        error: () => {
          const updated = [...this.projectStates()];
          updated[index] = { ...updated[index], mappings: [], loading: false };
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
      state.fetchError = 'Project must be linked to a provider with an external project key.';
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
          fetchError: 'Failed to fetch remote statuses. Please check the connection.',
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

    this.projectService.saveWorkflowMappings(state.project.id, validMappings).subscribe({
      next: (saved) => {
        const updated = [...this.projectStates()];
        updated[index] = {
          ...updated[index],
          mappings: saved,
          saving: false,
          saveSuccess: true,
          saveError: null,
        };
        this.projectStates.set(updated);
        setTimeout(() => {
          const current = [...this.projectStates()];
          current[index] = { ...current[index], saveSuccess: false };
          this.projectStates.set(current);
        }, 3000);
      },
      error: (err: any) => {
        const msg = err?.error?.message || 'Failed to save mappings. Please try again.';
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
    if (ps.expanded) {
      return `${ps.mappings.length} mapping${ps.mappings.length !== 1 ? 's' : ''}`;
    }
    return 'Not configured';
  }

  // --- Private helpers ---

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
      this.importSaveError.set(`${errors} project(s) failed to import. The rest were imported successfully.`);
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
    return { connectionId: '', name: '', publicKey: '', privateKey: '', keyType: 'ED25519' };
  }
}
