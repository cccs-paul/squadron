import { Component, inject, OnInit, signal, computed, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SlicePipe } from '@angular/common';
import { CdkDragDrop, CdkDrag, CdkDropList, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { forkJoin, Subscription, of, catchError } from 'rxjs';
import { TaskService } from '../../../core/services/task.service';
import { ProjectService } from '../../../core/services/project.service';
import { AgentService, AgentSession } from '../../../core/services/agent.service';
import { WorkspaceService } from '../../../core/services/workspace.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Task, TaskState, TaskPriority, TaskSyncRequest, TaskSyncResult, DelegateTaskRequest } from '../../../core/models/task.model';
import { Project, ProjectSummary } from '../../../core/models/project.model';

/** Enriched task with resolved project name and agent session info. */
export interface BoardTask extends Task {
  projectName?: string;
  agentStatus?: 'ACTIVE' | 'WAITING_INPUT' | 'COMPLETED' | 'FAILED' | null;
  agentSessionId?: string;
  agentType?: string;
  mappedExternalStatus?: string;
}

export interface BoardColumn {
  id: string;
  label: string;
  color: string;
  icon: string;
  states: TaskState[];
  tasks: BoardTask[];
}

/** Agent types available for delegation */
export const AGENT_TYPES = [
  { value: 'PLANNING', label: 'tasks.delegate.agentPlanning', icon: 'plan' },
  { value: 'CODING', label: 'tasks.delegate.agentCoding', icon: 'code' },
  { value: 'REVIEW', label: 'tasks.delegate.agentReview', icon: 'review' },
  { value: 'QA', label: 'tasks.delegate.agentQA', icon: 'qa' },
];

@Component({
  selector: 'sq-task-board',
  standalone: true,
  imports: [FormsModule, SlicePipe, CdkDropList, CdkDrag, TranslateModule],
  templateUrl: './task-board.component.html',
  styleUrl: './task-board.component.scss',
})
export class TaskBoardComponent implements OnInit, OnDestroy {
  private taskService = inject(TaskService);
  private projectService = inject(ProjectService);
  private agentService = inject(AgentService);
  private workspaceService = inject(WorkspaceService);
  private authService = inject(AuthService);
  private translate = inject(TranslateService);
  private router = inject(Router);
  private subscriptions: Subscription[] = [];

  columns = signal<BoardColumn[]>([]);
  loading = signal(true);
  filterPriority = signal('');
  filterProject = signal('');
  filterState = signal('');
  searchQuery = signal('');
  viewMode = signal<'board' | 'list'>('board');

  /** All projects indexed by id for quick lookup */
  projectMap = signal<Record<string, Project>>({});
  /** List of distinct projects for filter dropdown */
  projects = signal<Project[]>([]);
  /** Project summaries with task counts */
  projectSummaries = signal<ProjectSummary[]>([]);
  /** Active agent sessions indexed by taskId */
  agentSessions = signal<Record<string, AgentSession>>({});

  /** Task currently showing the action panel */
  expandedTaskId = signal<string | null>(null);
  /** Quick-prompt input text per task */
  promptText = signal<Record<string, string>>({});
  /** Cancelling state per task */
  cancellingTaskId = signal<string | null>(null);
  /** Sending prompt state per task */
  sendingPromptTaskId = signal<string | null>(null);

  /** Delegation panel state */
  showDelegatePanel = signal<string | null>(null);
  delegateAgentType = signal('PLANNING');
  delegateInstructions = signal('');
  delegating = signal(false);

  /** Sync panel state */
  showSyncPanel = signal(false);
  selectedSyncProjectId = signal('');
  syncing = signal(false);
  syncResult = signal<TaskSyncResult | null>(null);
  syncError = signal<string | null>(null);

  /** All unique states for state filter */
  allStates = Object.values(TaskState);
  agentTypes = AGENT_TYPES;

  /** Projects that can be synced (have connectionId + externalProjectId configured) */
  syncableProjects = computed(() =>
    this.projects().filter(p => p.connectionId && p.externalProjectId),
  );

  /** All tasks flat list for list view */
  allTasks = computed(() => {
    return this.filteredColumns().reduce((acc, col) => [...acc, ...col.tasks], [] as BoardTask[]);
  });

  filteredColumns = computed(() => {
    const cols = this.columns();
    const search = this.searchQuery().toLowerCase().trim();
    const priority = this.filterPriority();
    const projectId = this.filterProject();
    const state = this.filterState();

    if (!search && !priority && !projectId && !state) {
      return cols;
    }

    return cols.map(col => ({
      ...col,
      tasks: col.tasks.filter(task => {
        const matchesSearch = !search ||
          task.title.toLowerCase().includes(search) ||
          (task.description?.toLowerCase().includes(search)) ||
          (task.externalId?.toLowerCase().includes(search)) ||
          task.labels?.some(l => l.toLowerCase().includes(search)) ||
          (task.projectName?.toLowerCase().includes(search));
        const matchesPriority = !priority || task.priority === priority;
        const matchesProject = !projectId || task.projectId === projectId;
        const matchesState = !state || task.state === state;
        return matchesSearch && matchesPriority && matchesProject && matchesState;
      }),
    }));
  });

  /** Connected drop lists: restrict drag-drop to adjacent columns only */
  connectedDropLists = computed(() => {
    const cols = this.columns();
    return cols.map((col, idx) => {
      const connected: string[] = [];
      if (idx > 0) connected.push(`drop-list-${cols[idx - 1].id}`);
      if (idx < cols.length - 1) connected.push(`drop-list-${cols[idx + 1].id}`);
      return connected;
    });
  });

  /** Get connected drop list IDs for a specific column index */
  getConnectedLists(colIndex: number): string[] {
    return this.connectedDropLists()[colIndex] ?? [];
  }

  /** Total task counts for the summary bar */
  private static readonly PLANNED_STATES = new Set([TaskState.BACKLOG, TaskState.PRIORITIZED, TaskState.PLANNING]);
  private static readonly IN_PROGRESS_STATES = new Set([TaskState.PROPOSE_CODE, TaskState.IN_PROGRESS, TaskState.REVIEW, TaskState.QA, TaskState.MERGE]);
  private static readonly COMPLETED_STATES = new Set([TaskState.DONE]);

  totalPlanned = computed(() =>
    this.filteredColumns()
      .filter(c => TaskBoardComponent.PLANNED_STATES.has(c.states[0]))
      .reduce((sum, c) => sum + c.tasks.length, 0),
  );
  totalInProgress = computed(() =>
    this.filteredColumns()
      .filter(c => TaskBoardComponent.IN_PROGRESS_STATES.has(c.states[0]))
      .reduce((sum, c) => sum + c.tasks.length, 0),
  );
  totalCompleted = computed(() =>
    this.filteredColumns()
      .filter(c => TaskBoardComponent.COMPLETED_STATES.has(c.states[0]))
      .reduce((sum, c) => sum + c.tasks.length, 0),
  );
  totalTasks = computed(() => this.totalInProgress() + this.totalPlanned() + this.totalCompleted());

  ngOnInit(): void {
    this.loadData();
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(s => s.unsubscribe());
  }

  loadData(): void {
    this.loading.set(true);
    const user = this.authService.user();
    const tenantId = user?.tenantId;

    const sources: Record<string, any> = {
      tasksByState: this.taskService.getTasksByState(),
    };
    if (tenantId) {
      sources['projects'] = this.projectService.getProjectsByTenant(tenantId);
      sources['summaries'] = this.projectService.getProjectSummaries(tenantId).pipe(
        catchError(() => of([] as ProjectSummary[])),
      );
    }

    forkJoin(sources).subscribe({
      next: (results: any) => {
        const tasksByState: Record<TaskState, Task[]> = results.tasksByState;
        const projectList: Project[] = results.projects ?? [];
        const summaries: ProjectSummary[] = results.summaries ?? [];

        // Build project map
        const pMap: Record<string, Project> = {};
        projectList.forEach(p => pMap[p.id] = p);
        this.projectMap.set(pMap);
        this.projects.set(projectList);
        this.projectSummaries.set(summaries);

        // Build columns
        this.buildColumns(tasksByState, pMap);
        this.loading.set(false);

        // Load agent sessions for active tasks
        this.loadAgentSessions();
      },
      error: () => {
        this.buildColumns({} as any, {});
        this.loading.set(false);
      },
    });
  }

  /** Column definitions for the 9-column board — one column per TaskState. */
  private static readonly COLUMN_DEFS: { id: string; state: TaskState; label: string; color: string; icon: string }[] = [
    { id: 'backlog',      state: TaskState.BACKLOG,       label: 'tasks.board.column.backlog',      color: '#94A3B8', icon: 'inbox' },
    { id: 'prioritized',  state: TaskState.PRIORITIZED,   label: 'tasks.board.column.prioritized',  color: '#A78BFA', icon: 'star' },
    { id: 'planning',     state: TaskState.PLANNING,      label: 'tasks.board.column.planning',     color: '#818CF8', icon: 'calendar' },
    { id: 'propose-code', state: TaskState.PROPOSE_CODE,  label: 'tasks.board.column.proposeCode',  color: '#38BDF8', icon: 'code' },
    { id: 'in-progress',  state: TaskState.IN_PROGRESS,   label: 'tasks.board.column.inProgress',   color: '#06B6D4', icon: 'play' },
    { id: 'review',       state: TaskState.REVIEW,        label: 'tasks.board.column.review',       color: '#F59E0B', icon: 'review' },
    { id: 'qa',           state: TaskState.QA,            label: 'tasks.board.column.qa',            color: '#8B5CF6', icon: 'qa' },
    { id: 'merge',        state: TaskState.MERGE,         label: 'tasks.board.column.merge',         color: '#22C55E', icon: 'merge' },
    { id: 'done',         state: TaskState.DONE,          label: 'tasks.board.column.done',          color: '#10B981', icon: 'check' },
  ];

  private buildColumns(tasksByState: Record<string, Task[]>, pMap: Record<string, Project>): void {
    const enrichTask = (task: Task): BoardTask => ({
      ...task,
      projectName: pMap[task.projectId]?.name,
    });

    this.columns.set(
      TaskBoardComponent.COLUMN_DEFS.map(def => ({
        id: def.id,
        label: def.label,
        color: def.color,
        icon: def.icon,
        states: [def.state],
        tasks: (tasksByState[def.state] ?? []).map(enrichTask),
      })),
    );
  }

  /** States whose tasks may have active agent sessions */
  private static readonly AGENT_SESSION_STATES = new Set([
    TaskState.PROPOSE_CODE, TaskState.IN_PROGRESS, TaskState.REVIEW, TaskState.QA, TaskState.MERGE,
  ]);

  private loadAgentSessions(): void {
    const activeTasks = this.columns()
      .filter(c => TaskBoardComponent.AGENT_SESSION_STATES.has(c.states[0]))
      .flatMap(c => c.tasks);

    activeTasks.forEach(task => {
      const sub = this.agentService.getSession(task.id).subscribe({
        next: (session) => {
          const sessions = { ...this.agentSessions() };
          sessions[task.id] = session;
          this.agentSessions.set(sessions);
          this.updateTaskAgentStatus(task.id, session);
        },
        error: () => { /* No session found -- normal for most tasks */ },
      });
      this.subscriptions.push(sub);
    });
  }

  private updateTaskAgentStatus(taskId: string, session: AgentSession): void {
    const cols = this.columns().map(col => ({
      ...col,
      tasks: col.tasks.map(t => {
        if (t.id === taskId) {
          return { ...t, agentStatus: session.status, agentType: this.getAgentTypeFromSession(session) };
        }
        return t;
      }),
    }));
    this.columns.set(cols);
  }

  private getAgentTypeFromSession(session: AgentSession): string | undefined {
    const systemMsg = session.messages?.find(m => m.role === 'SYSTEM');
    if (systemMsg?.content?.includes('Planner')) return 'Planner';
    if (systemMsg?.content?.includes('Coder')) return 'Coder';
    if (systemMsg?.content?.includes('Reviewer')) return 'Reviewer';
    if (systemMsg?.content?.includes('QA')) return 'QA';
    return undefined;
  }

  // --- User actions ---

  openTask(task: Task): void {
    this.router.navigate(['/tasks', task.id]);
  }

  openAgentChat(task: BoardTask): void {
    this.router.navigate(['/agent', task.id]);
  }

  toggleTaskActions(taskId: string): void {
    if (this.expandedTaskId() === taskId) {
      this.expandedTaskId.set(null);
      this.showDelegatePanel.set(null);
    } else {
      this.expandedTaskId.set(taskId);
      this.showDelegatePanel.set(null);
    }
  }

  getStateBadge(task: BoardTask): string {
    if (!task.state) return '';
    return this.translate.instant(`tasks.state.${task.state}`);
  }

  getAgentStatusLabel(task: BoardTask): string {
    if (!task.agentStatus) return '';
    const key = `tasks.board.agentStatus.${task.agentStatus.toLowerCase()}`;
    return this.translate.instant(key);
  }

  priorityBadgeClass(priority: TaskPriority): string {
    switch (priority) {
      case TaskPriority.CRITICAL: return 'error';
      case TaskPriority.HIGH: return 'warning';
      case TaskPriority.MEDIUM: return 'primary';
      case TaskPriority.LOW: return 'neutral';
      default: return 'neutral';
    }
  }

  stateColor(state: string): string {
    switch (state) {
      case TaskState.BACKLOG: return '#94A3B8';
      case TaskState.PRIORITIZED: return '#A78BFA';
      case TaskState.PLANNING: return '#818CF8';
      case TaskState.PROPOSE_CODE: return '#38BDF8';
      case TaskState.IN_PROGRESS: return '#06B6D4';
      case TaskState.REVIEW: return '#F59E0B';
      case TaskState.QA: return '#8B5CF6';
      case TaskState.MERGE: return '#22C55E';
      case TaskState.DONE: return '#10B981';
      default: return '#94A3B8';
    }
  }

  // --- Delegate to agent ---

  toggleDelegatePanel(taskId: string): void {
    if (this.showDelegatePanel() === taskId) {
      this.showDelegatePanel.set(null);
    } else {
      this.showDelegatePanel.set(taskId);
      this.delegateAgentType.set('PLANNING');
      this.delegateInstructions.set('');
    }
  }

  delegateTask(task: BoardTask): void {
    const request: DelegateTaskRequest = {
      agentType: this.delegateAgentType(),
      instructions: this.delegateInstructions() || undefined,
      targetState: this.delegateAgentType(),
    };

    this.delegating.set(true);
    this.taskService.delegateToAgent(task.id, request).subscribe({
      next: () => {
        this.delegating.set(false);
        this.showDelegatePanel.set(null);
        this.expandedTaskId.set(null);
        // Reload to show updated state
        this.loadData();
      },
      error: () => {
        this.delegating.set(false);
      },
    });
  }

  // --- Cancel task ---

  cancelTask(task: BoardTask): void {
    this.cancellingTaskId.set(task.id);

    if (task.agentSessionId && task.agentStatus === 'ACTIVE') {
      this.agentService.interruptAgent(task.agentSessionId, 'USER_CANCEL').subscribe({
        next: () => this.transitionToBacklog(task),
        error: () => this.transitionToBacklog(task),
      });
    } else {
      this.transitionToBacklog(task);
    }
  }

  private transitionToBacklog(task: BoardTask): void {
    this.taskService.transitionTask(task.id, TaskState.BACKLOG).subscribe({
      next: () => {
        this.moveTaskToColumn(task, 'backlog');
        this.cancellingTaskId.set(null);
        this.cleanupWorkspace(task.id);
      },
      error: () => {
        this.cancellingTaskId.set(null);
      },
    });
  }

  private cleanupWorkspace(taskId: string): void {
    this.workspaceService.getWorkspaceByTask(taskId).subscribe({
      next: (workspace) => {
        if (workspace && workspace.status !== 'STOPPED') {
          this.workspaceService.destroyWorkspace(workspace.id).subscribe();
        }
      },
      error: () => { /* No workspace or error - ignore */ },
    });
  }

  private moveTaskToColumn(task: BoardTask, targetColumnId: string): void {
    const cols = this.columns().map(col => {
      if (col.id === targetColumnId) {
        const updatedTask = { ...task, state: TaskState.BACKLOG, agentStatus: null as any };
        return { ...col, tasks: [updatedTask, ...col.tasks] };
      }
      return { ...col, tasks: col.tasks.filter(t => t.id !== task.id) };
    });
    this.columns.set(cols);
    this.expandedTaskId.set(null);
  }

  // --- Quick prompt ---

  sendPrompt(task: BoardTask): void {
    const text = this.promptText()[task.id]?.trim();
    if (!text || !task.agentSessionId) return;

    this.sendingPromptTaskId.set(task.id);
    this.agentService.sendMessage(task.agentSessionId, text).subscribe({
      next: () => {
        const prompts = { ...this.promptText() };
        prompts[task.id] = '';
        this.promptText.set(prompts);
        this.sendingPromptTaskId.set(null);
      },
      error: () => {
        this.sendingPromptTaskId.set(null);
      },
    });
  }

  updatePromptText(taskId: string, value: string): void {
    const prompts = { ...this.promptText() };
    prompts[taskId] = value;
    this.promptText.set(prompts);
  }

  // --- Plan approval ---

  approvePlan(task: BoardTask): void {
    const session = this.agentSessions()[task.id];
    if (!session?.currentPlan || !task.agentSessionId) return;

    this.agentService.approvePlan(task.agentSessionId, session.currentPlan.id).subscribe({
      next: () => {
        this.agentService.getSession(task.id).subscribe({
          next: (updated) => {
            this.updateTaskAgentStatus(task.id, updated);
            const sessions = { ...this.agentSessions() };
            sessions[task.id] = updated;
            this.agentSessions.set(sessions);
          },
        });
      },
    });
  }

  rejectPlan(task: BoardTask): void {
    const session = this.agentSessions()[task.id];
    if (!session?.currentPlan || !task.agentSessionId) return;

    const feedback = this.promptText()[task.id]?.trim() || 'Rejected by user';
    this.agentService.rejectPlan(task.agentSessionId, session.currentPlan.id, feedback).subscribe({
      next: () => {
        const prompts = { ...this.promptText() };
        prompts[task.id] = '';
        this.promptText.set(prompts);
        this.agentService.getSession(task.id).subscribe({
          next: (updated) => {
            this.updateTaskAgentStatus(task.id, updated);
            const sessions = { ...this.agentSessions() };
            sessions[task.id] = updated;
            this.agentSessions.set(sessions);
          },
        });
      },
    });
  }

  hasActivePlan(task: BoardTask): boolean {
    const session = this.agentSessions()[task.id];
    return !!session?.currentPlan && !session.currentPlan.approved;
  }

  // --- Task sync ---

  toggleSyncPanel(): void {
    this.showSyncPanel.set(!this.showSyncPanel());
    if (!this.showSyncPanel()) {
      this.resetSyncState();
    }
  }

  closeSyncPanel(): void {
    this.showSyncPanel.set(false);
    this.resetSyncState();
  }

  private resetSyncState(): void {
    this.selectedSyncProjectId.set('');
    this.syncResult.set(null);
    this.syncError.set(null);
  }

  syncTasks(): void {
    const projectId = this.selectedSyncProjectId();
    if (!projectId) return;

    const project = this.projects().find(p => p.id === projectId);
    if (!project || !project.connectionId || !project.externalProjectId) return;

    const user = this.authService.user();
    if (!user?.tenantId) return;

    const request: TaskSyncRequest = {
      tenantId: user.tenantId,
      teamId: project.teamId,
      projectId: project.id,
      platformConnectionId: project.connectionId,
      projectKey: project.externalProjectId,
    };

    this.syncing.set(true);
    this.syncResult.set(null);
    this.syncError.set(null);

    this.taskService.syncTasks(request).subscribe({
      next: (result) => {
        this.syncResult.set(result);
        this.syncing.set(false);
        if (result.created > 0 || result.updated > 0) {
          this.loadData();
        }
      },
      error: (err) => {
        this.syncError.set(err?.message || 'Sync failed');
        this.syncing.set(false);
      },
    });
  }

  // --- Drag-drop ---

  drop(event: CdkDragDrop<BoardTask[]>, targetColumn: BoardColumn): void {
    if (event.previousContainer === event.container) {
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
    } else {
      const task = event.previousContainer.data[event.previousIndex];
      transferArrayItem(
        event.previousContainer.data,
        event.container.data,
        event.previousIndex,
        event.currentIndex,
      );

      const targetState = targetColumn.states[0];
      this.taskService.transitionTask(task.id, targetState).subscribe({
        next: () => {
          // Update the task's state to reflect the new column
          task.state = targetState;
        },
        error: () => {
          transferArrayItem(
            event.container.data,
            event.previousContainer.data,
            event.currentIndex,
            event.previousIndex,
          );
        },
      });
    }
  }

  // --- View mode toggle ---

  setViewMode(mode: 'board' | 'list'): void {
    this.viewMode.set(mode);
  }

  // --- Refresh ---

  refresh(): void {
    this.loadData();
  }
}
