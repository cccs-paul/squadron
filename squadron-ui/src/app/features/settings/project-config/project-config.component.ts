import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProjectService } from '../../../core/services/project.service';
import { PlatformService } from '../../../core/services/platform.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Project, WorkflowMapping } from '../../../core/models/project.model';
import {
  PlatformConnection,
  PlatformConnectionType,
  CreateConnectionRequest,
} from '../../../core/models/security.model';
import { forkJoin } from 'rxjs';

type TabId = 'providers' | 'projects';

interface ProviderForm {
  name: string;
  platformType: string;
  baseUrl: string;
  authType: string;
  credentials: Record<string, string>;
}

interface ProjectForm {
  name: string;
  description: string;
  defaultBranch: string;
  connectionId: string;
  externalProjectId: string;
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

const AUTH_TYPE_OPTIONS: Record<string, { label: string; fields: { key: string; label: string; secret: boolean }[] }[]> = {
  JIRA: [
    { label: 'API Token', fields: [{ key: 'email', label: 'Email', secret: false }, { key: 'apiToken', label: 'API Token', secret: true }] },
    { label: 'PAT', fields: [{ key: 'pat', label: 'Personal Access Token', secret: true }] },
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
  private authService = inject(AuthService);

  loading = signal(true);
  loadError = signal<string | null>(null);
  activeTab = signal<TabId>('providers');

  // Providers tab
  connections = signal<PlatformConnection[]>([]);
  showProviderForm = signal(false);
  savingProvider = signal(false);
  providerSaveError = signal<string | null>(null);
  deletingConnectionId = signal<string | null>(null);
  providerForm: ProviderForm = this.newProviderForm();

  // Projects tab
  projectStates = signal<ProjectMappingState[]>([]);
  workflowStates = signal<string[]>([]);
  showProjectForm = signal(false);
  savingProject = signal(false);
  projectSaveError = signal<string | null>(null);
  projectForm: ProjectForm = this.newProjectForm();

  readonly platformTypes = Object.values(PlatformConnectionType);

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
    }).subscribe({
      next: ({ projects, states, connections }) => {
        this.workflowStates.set(states);
        this.connections.set(connections);
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
        this.workflowStates.set([
          'BACKLOG', 'PRIORITIZED', 'PLANNING', 'PROPOSE_CODE',
          'REVIEW', 'QA', 'MERGE', 'DONE',
        ]);
        this.connections.set([]);
        this.projectStates.set([]);
        this.loading.set(false);
      },
    });
  }

  setTab(tab: TabId): void {
    this.activeTab.set(tab);
  }

  // --- Provider methods ---

  getAuthTypeOptions(): { label: string; fields: { key: string; label: string; secret: boolean }[] }[] {
    return AUTH_TYPE_OPTIONS[this.providerForm.platformType] ?? [];
  }

  getAuthFields(): { key: string; label: string; secret: boolean }[] {
    const options = this.getAuthTypeOptions();
    const selected = options.find((o) => o.label === this.providerForm.authType);
    return selected?.fields ?? [];
  }

  onPlatformTypeChange(): void {
    const options = this.getAuthTypeOptions();
    this.providerForm.authType = options.length > 0 ? options[0].label : '';
    this.providerForm.credentials = {};
  }

  onAuthTypeChange(): void {
    this.providerForm.credentials = {};
  }

  toggleProviderForm(): void {
    this.showProviderForm.set(!this.showProviderForm());
    if (!this.showProviderForm()) {
      this.providerForm = this.newProviderForm();
      this.providerSaveError.set(null);
    }
  }

  canSaveProvider(): boolean {
    const f = this.providerForm;
    if (!f.name.trim() || !f.platformType || !f.baseUrl.trim() || !f.authType) return false;
    const fields = this.getAuthFields();
    return fields.every((field) => (f.credentials[field.key] ?? '').trim().length > 0);
  }

  saveProvider(): void {
    const user = this.authService.user();
    if (!user) return;

    this.savingProvider.set(true);
    this.providerSaveError.set(null);

    const request: CreateConnectionRequest = {
      tenantId: user.tenantId,
      name: this.providerForm.name.trim(),
      platformType: this.providerForm.platformType,
      baseUrl: this.providerForm.baseUrl.trim(),
      authType: this.providerForm.authType,
      credentials: { ...this.providerForm.credentials },
    };

    this.platformService.createConnectionFromRequest(request).subscribe({
      next: (connection) => {
        this.connections.set([...this.connections(), connection]);
        this.savingProvider.set(false);
        this.showProviderForm.set(false);
        this.providerForm = this.newProviderForm();
      },
      error: () => {
        this.providerSaveError.set('Failed to save provider. Please check your configuration and try again.');
        this.savingProvider.set(false);
      },
    });
  }

  deleteConnection(id: string): void {
    this.deletingConnectionId.set(id);
    this.platformService.deleteConnection(id).subscribe({
      next: () => {
        this.connections.set(this.connections().filter((c) => c.id !== id));
        this.deletingConnectionId.set(null);
      },
      error: () => {
        this.deletingConnectionId.set(null);
      },
    });
  }

  // --- Project methods ---

  toggleProjectForm(): void {
    this.showProjectForm.set(!this.showProjectForm());
    if (!this.showProjectForm()) {
      this.projectForm = this.newProjectForm();
      this.projectSaveError.set(null);
    }
  }

  canSaveProject(): boolean {
    const f = this.projectForm;
    return f.name.trim().length > 0 && f.connectionId.length > 0;
  }

  saveProject(): void {
    this.savingProject.set(true);
    this.projectSaveError.set(null);

    const project: Partial<Project> = {
      name: this.projectForm.name.trim(),
      description: this.projectForm.description.trim() || undefined,
      defaultBranch: this.projectForm.defaultBranch.trim() || 'main',
      connectionId: this.projectForm.connectionId,
      externalProjectId: this.projectForm.externalProjectId.trim() || undefined,
    };

    this.projectService.createProject(project).subscribe({
      next: (created) => {
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
          connectionName: this.getConnectionName(created.connectionId, this.connections()),
        };
        this.projectStates.set([...this.projectStates(), newState]);
        this.savingProject.set(false);
        this.showProjectForm.set(false);
        this.projectForm = this.newProjectForm();
      },
      error: () => {
        this.projectSaveError.set('Failed to create project. Please try again.');
        this.savingProject.set(false);
      },
    });
  }

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
        const updated = [...this.projectStates()];
        const conn = this.connections().find((c) => c.id === project.connectionId);
        const platformType = conn?.platformType ?? 'UNKNOWN';
        const mockStatuses = this.getMockStatuses(platformType);
        updated[index] = {
          ...updated[index],
          remoteStatuses: mockStatuses,
          fetchingStatuses: false,
          fetchError: null,
        };
        this.projectStates.set(updated);
      },
    });
  }

  updateProjectConnection(index: number, connectionId: string): void {
    const states = [...this.projectStates()];
    const state = { ...states[index] };
    state.project = { ...state.project, connectionId: connectionId || undefined };
    state.connectionName = this.getConnectionName(connectionId, this.connections());
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
      error: () => {
        const updated = [...this.projectStates()];
        updated[index] = {
          ...updated[index],
          saving: false,
          saveError: 'Failed to save mappings. Please try again.',
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

  platformIcon(type: string): string {
    switch (type) {
      case 'GITHUB': return 'GitHub';
      case 'GITLAB': return 'GitLab';
      case 'JIRA': case 'JIRA_CLOUD': case 'JIRA_SERVER': return 'Jira';
      case 'AZURE_DEVOPS': return 'Azure DevOps';
      case 'BITBUCKET': return 'Bitbucket';
      default: return type;
    }
  }

  canFetchStatuses(ps: ProjectMappingState): boolean {
    return !!ps.project.connectionId && !!ps.project.externalProjectId && !ps.fetchingStatuses;
  }

  private getConnectionName(connectionId: string | undefined, connections: PlatformConnection[]): string | null {
    if (!connectionId) return null;
    const conn = connections.find((c) => c.id === connectionId);
    return conn ? conn.name : null;
  }

  private getMockStatuses(platformType: string): string[] {
    switch (platformType) {
      case 'JIRA': case 'JIRA_CLOUD': case 'JIRA_SERVER':
        return ['To Do', 'In Progress', 'Code Review', 'QA Testing', 'Done'];
      case 'GITHUB':
        return ['open', 'closed'];
      case 'GITLAB':
        return ['opened', 'closed'];
      case 'AZURE_DEVOPS':
        return ['New', 'Active', 'Resolved', 'Closed'];
      default:
        return ['Open', 'In Progress', 'Done'];
    }
  }

  private newProviderForm(): ProviderForm {
    return { name: '', platformType: 'JIRA', baseUrl: '', authType: 'API Token', credentials: {} };
  }

  private newProjectForm(): ProjectForm {
    return { name: '', description: '', defaultBranch: 'main', connectionId: '', externalProjectId: '' };
  }
}
