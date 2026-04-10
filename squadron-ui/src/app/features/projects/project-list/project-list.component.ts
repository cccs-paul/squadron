import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ProjectService } from '../../../core/services/project.service';
import { TaskService } from '../../../core/services/task.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Project, ProjectSummary } from '../../../core/models/project.model';
import { TaskSyncRequest, TaskSyncResult } from '../../../core/models/task.model';
import { forkJoin, of, catchError } from 'rxjs';

@Component({
  selector: 'sq-project-list',
  standalone: true,
  imports: [FormsModule, TranslateModule],
  templateUrl: './project-list.component.html',
  styleUrl: './project-list.component.scss',
})
export class ProjectListComponent implements OnInit {
  private projectService = inject(ProjectService);
  private taskService = inject(TaskService);
  private authService = inject(AuthService);
  private translate = inject(TranslateService);
  private router = inject(Router);

  projects = signal<Project[]>([]);
  summaries = signal<ProjectSummary[]>([]);
  loading = signal(true);
  searchQuery = signal('');
  viewMode = signal<'grid' | 'list'>('grid');

  /** Sync state per project */
  syncingProjectId = signal<string | null>(null);
  syncResults = signal<Record<string, TaskSyncResult>>({});
  syncErrors = signal<Record<string, string>>({});

  /** Summary lookup by project ID */
  summaryMap = computed(() => {
    const map: Record<string, ProjectSummary> = {};
    this.summaries().forEach(s => map[s.id] = s);
    return map;
  });

  filteredProjects = computed(() => {
    const query = this.searchQuery().toLowerCase().trim();
    const projs = this.projects();
    if (!query) return projs;
    return projs.filter(p =>
      p.name.toLowerCase().includes(query) ||
      (p.description?.toLowerCase().includes(query)) ||
      (p.externalProjectId?.toLowerCase().includes(query)) ||
      (p.repositoryUrl?.toLowerCase().includes(query)),
    );
  });

  /** Aggregate stats */
  totalProjects = computed(() => this.filteredProjects().length);
  totalTasks = computed(() => this.summaries().reduce((sum, s) => sum + s.totalTasks, 0));
  totalActiveTasks = computed(() => this.summaries().reduce((sum, s) => sum + s.activeTasks, 0));
  configuredProjects = computed(() => this.summaries().filter(s => s.workflowMappingsConfigured).length);

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    const user = this.authService.user();
    const tenantId = user?.tenantId;
    if (!tenantId) {
      this.loading.set(false);
      return;
    }

    forkJoin({
      projects: this.projectService.getProjectsByTenant(tenantId).pipe(catchError(() => of([] as Project[]))),
      summaries: this.projectService.getProjectSummaries(tenantId).pipe(catchError(() => of([] as ProjectSummary[]))),
    }).subscribe({
      next: ({ projects, summaries }) => {
        this.projects.set(projects);
        this.summaries.set(summaries);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      },
    });
  }

  getSummary(projectId: string): ProjectSummary | undefined {
    return this.summaryMap()[projectId];
  }

  getTaskStateEntries(summary: ProjectSummary): { state: string; count: number }[] {
    if (!summary?.taskCountsByState) return [];
    return Object.entries(summary.taskCountsByState)
      .filter(([_, count]) => count > 0)
      .map(([state, count]) => ({ state, count }))
      .sort((a, b) => b.count - a.count);
  }

  stateColor(state: string): string {
    switch (state) {
      case 'BACKLOG': return '#94A3B8';
      case 'PRIORITIZED': return '#A78BFA';
      case 'PLANNING': return '#818CF8';
      case 'PROPOSE_CODE': return '#38BDF8';
      case 'IN_PROGRESS': return '#06B6D4';
      case 'REVIEW': return '#F59E0B';
      case 'QA': return '#8B5CF6';
      case 'MERGE': return '#22C55E';
      case 'DONE': return '#10B981';
      default: return '#94A3B8';
    }
  }

  syncProject(project: Project): void {
    if (!project.connectionId || !project.externalProjectId) return;
    const user = this.authService.user();
    if (!user?.tenantId) return;

    const request: TaskSyncRequest = {
      tenantId: user.tenantId,
      teamId: project.teamId,
      projectId: project.id,
      platformConnectionId: project.connectionId,
      projectKey: project.externalProjectId,
    };

    this.syncingProjectId.set(project.id);

    // Clear previous results for this project
    const results = { ...this.syncResults() };
    delete results[project.id];
    this.syncResults.set(results);
    const errors = { ...this.syncErrors() };
    delete errors[project.id];
    this.syncErrors.set(errors);

    this.taskService.syncTasks(request).subscribe({
      next: (result) => {
        const newResults = { ...this.syncResults() };
        newResults[project.id] = result;
        this.syncResults.set(newResults);
        this.syncingProjectId.set(null);
        if (result.created > 0 || result.updated > 0) {
          this.loadData();
        }
      },
      error: (err) => {
        const newErrors = { ...this.syncErrors() };
        newErrors[project.id] = err?.message || 'Sync failed';
        this.syncErrors.set(newErrors);
        this.syncingProjectId.set(null);
      },
    });
  }

  canSync(project: Project): boolean {
    return !!(project.connectionId && project.externalProjectId);
  }

  openProject(project: Project): void {
    this.router.navigate(['/projects', project.id]);
  }

  openSettings(): void {
    this.router.navigate(['/settings']);
  }

  setViewMode(mode: 'grid' | 'list'): void {
    this.viewMode.set(mode);
  }

  refresh(): void {
    this.loadData();
  }
}
