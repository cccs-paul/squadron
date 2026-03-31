import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProjectService } from '../../../core/services/project.service';
import { PlatformService } from '../../../core/services/platform.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Project, WorkflowMapping } from '../../../core/models/project.model';
import { PlatformConnection, PlatformConnectionType } from '../../../core/models/security.model';
import { forkJoin } from 'rxjs';

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
  projectStates = signal<ProjectMappingState[]>([]);
  workflowStates = signal<string[]>([]);
  connections = signal<PlatformConnection[]>([]);

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
        this.connections.set(this.getMockConnections());
        this.projectStates.set(this.getMockProjectStates());
        this.loading.set(false);
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
      state.fetchError = 'Project must be linked to a platform connection with an external project key.';
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
        // Provide mock statuses based on platform type for demo
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

  private getMockConnections(): PlatformConnection[] {
    return [
      {
        id: 'pc-1', tenantId: '1', name: 'Jira Cloud - Production',
        platformType: PlatformConnectionType.JIRA,
        baseUrl: 'https://myorg.atlassian.net',
        status: 'CONNECTED' as any, config: {},
        createdAt: new Date(Date.now() - 86400000 * 30).toISOString(),
      },
      {
        id: 'pc-2', tenantId: '1', name: 'GitHub - Organization',
        platformType: PlatformConnectionType.GITHUB,
        baseUrl: 'https://api.github.com',
        status: 'CONNECTED' as any, config: {},
        createdAt: new Date(Date.now() - 86400000 * 20).toISOString(),
      },
    ];
  }

  private getMockProjectStates(): ProjectMappingState[] {
    return [
      {
        project: {
          id: '1', tenantId: '1', name: 'squadron-api',
          description: 'Main backend API service', defaultBranch: 'main',
          connectionId: 'pc-1', externalProjectId: 'SQ',
          taskCount: 24, activeTaskCount: 8, members: [],
          createdAt: new Date(Date.now() - 604800000).toISOString(),
        },
        expanded: false, mappings: [], remoteStatuses: [],
        saving: false, saveSuccess: false, saveError: null,
        loading: false, fetchingStatuses: false, fetchError: null,
        connectionName: 'Jira Cloud - Production',
      },
      {
        project: {
          id: '2', tenantId: '1', name: 'squadron-ui',
          description: 'Angular frontend application', defaultBranch: 'main',
          connectionId: 'pc-2', externalProjectId: 'squadron-ui',
          taskCount: 15, activeTaskCount: 5, members: [],
          createdAt: new Date(Date.now() - 432000000).toISOString(),
        },
        expanded: false, mappings: [], remoteStatuses: [],
        saving: false, saveSuccess: false, saveError: null,
        loading: false, fetchingStatuses: false, fetchError: null,
        connectionName: 'GitHub - Organization',
      },
    ];
  }
}
