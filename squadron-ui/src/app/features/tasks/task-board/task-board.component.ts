import { Component, inject, OnInit, signal, computed, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SlicePipe, DatePipe } from '@angular/common';
import { CdkDragDrop, CdkDrag, CdkDropList, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { forkJoin, Subscription, of, catchError } from 'rxjs';
import { TaskService } from '../../../core/services/task.service';
import { ProjectService } from '../../../core/services/project.service';
import { AgentService, AgentSession, AgentMessage } from '../../../core/services/agent.service';
import { WorkspaceService } from '../../../core/services/workspace.service';
import { AuthService } from '../../../core/auth/auth.service';
import { UserSquadronService } from '../../../core/services/user-squadron.service';
import { Task, TaskState, TaskPriority, TaskSyncRequest, TaskSyncResult, DelegateTaskRequest, TicketlessTask, TicketlessStatus, CreateTicketlessTaskRequest } from '../../../core/models/task.model';
import { Project, ProjectSummary, WorkflowMapping } from '../../../core/models/project.model';
import { ConversationSummary } from '../../../core/models/agent.model';
import { UserAgentConfig } from '../../../core/models/squadron-config.model';

/** Enriched task with resolved project name and agent session info. */
export interface BoardTask extends Task {
  projectName?: string;
  agentStatus?: 'ACTIVE' | 'WAITING_INPUT' | 'COMPLETED' | 'FAILED' | null;
  agentSessionId?: string;
  agentType?: string;
  mappedExternalStatus?: string;
  /** Number of agent conversations associated with this task */
  conversationCount?: number;
  /** Conversation summaries for this task */
  conversations?: ConversationSummary[];
}

export interface BoardColumn {
  id: string;
  label: string;
  color: string;
  icon: string;
  states: TaskState[];
  tasks: BoardTask[];
  /** When viewing a specific project, the external status mapped to this internal state */
  externalStatus?: string;
}

/** Agent types available for delegation */
export const AGENT_TYPES = [
  { value: 'PLANNING', label: 'tasks.delegate.agentPlanning', icon: 'plan' },
  { value: 'CODING', label: 'tasks.delegate.agentCoding', icon: 'code' },
  { value: 'REVIEW', label: 'tasks.delegate.agentReview', icon: 'review' },
  { value: 'QA', label: 'tasks.delegate.agentQA', icon: 'qa' },
];

/** Maps a TaskState to the recommended agent type for that state */
const STATE_AGENT_MAP: Record<string, string> = {
  [TaskState.PLANNING]: 'PLANNING',
  [TaskState.PROPOSE_CODE]: 'CODING',
  [TaskState.IN_PROGRESS]: 'CODING',
  [TaskState.REVIEW]: 'REVIEW',
  [TaskState.QA]: 'QA',
  [TaskState.MERGE]: 'CODING',
};

@Component({
  selector: 'sq-task-board',
  standalone: true,
  imports: [FormsModule, SlicePipe, DatePipe, CdkDropList, CdkDrag, TranslateModule],
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
  private userSquadronService = inject(UserSquadronService);
  private router = inject(Router);
  private subscriptions: Subscription[] = [];

  columns = signal<BoardColumn[]>([]);
  loading = signal(true);
  filterPriority = signal('');
  filterProject = signal('');
  filterState = signal('');
  searchQuery = signal('');
  viewMode = signal<'board' | 'list'>('board');

  /** Currently selected project ID — always a real project ID (no "all projects") */
  selectedProjectId = signal('');

  /** All projects indexed by id for quick lookup */
  projectMap = signal<Record<string, Project>>({});
  /** List of distinct projects for filter dropdown */
  projects = signal<Project[]>([]);
  /** Project summaries with task counts */
  projectSummaries = signal<ProjectSummary[]>([]);
  /** Active agent sessions indexed by taskId */
  agentSessions = signal<Record<string, AgentSession>>({});
  /** Conversation summaries indexed by taskId */
  taskConversations = signal<Record<string, ConversationSummary[]>>({});
  /** Workflow mappings for the currently selected project */
  workflowMappings = signal<WorkflowMapping[]>([]);
  /** Raw tasks by state from the API */
  private rawTasksByState = signal<Record<string, Task[]>>({});

  /** Task currently showing the action panel */
  expandedTaskId = signal<string | null>(null);
  /** Task currently showing the full agent history panel */
  expandedHistoryTaskId = signal<string | null>(null);
  /** Full messages loaded for a task's agent session */
  taskMessages = signal<Record<string, AgentMessage[]>>({});
  /** Loading state for message fetching */
  loadingMessages = signal<string | null>(null);
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

  /** Drag-drop modal: shown after dropping a task to a new column */
  dropModal = signal<{
    task: BoardTask;
    fromState: TaskState;
    toState: TaskState;
    suggestedAgent: string;
    instructions: string;
  } | null>(null);
  dropModalDelegating = signal(false);

  /** Sync panel state */
  showSyncPanel = signal(false);
  selectedSyncProjectId = signal('');
  syncing = signal(false);
  syncResult = signal<TaskSyncResult | null>(null);
  syncError = signal<string | null>(null);

  /** All unique states for state filter */
  allStates = Object.values(TaskState);
  agentTypes = AGENT_TYPES;

  /** Ticketless tasks */
  ticketlessTasks = signal<TicketlessTask[]>([]);
  /** Available agents for ticketless task creation */
  availableAgents = signal<UserAgentConfig[]>([]);
  /** Ticketless create dialog */
  showTicketlessDialog = signal(false);
  ticketlessTitle = signal('');
  ticketlessPrompt = signal('');
  ticketlessBranch = signal('');
  ticketlessCreateBranch = signal(false);
  ticketlessAgentMode = signal<'PLAN' | 'BUILD'>('BUILD');
  ticketlessAgentConfigId = signal('');
  ticketlessProjectId = signal('');
  creatingTicketless = signal(false);

  /** Projects that can be synced (have connectionId + externalProjectId configured) */
  syncableProjects = computed(() =>
    this.projects().filter(p => p.connectionId && p.externalProjectId),
  );

  /** Projects that have a configured Git remote (gitConnectionId) */
  gitRemoteProjects = computed(() =>
    this.projects().filter(p => p.gitConnectionId),
  );

  /** All tasks flat list for list view */
  allTasks = computed(() => {
    return this.filteredColumns().reduce((acc, col) => [...acc, ...col.tasks], [] as BoardTask[]);
  });

  /** The currently selected project object */
  selectedProject = computed(() => {
    const id = this.selectedProjectId();
    return id ? this.projectMap()[id] : null;
  });

  filteredColumns = computed(() => {
    const cols = this.columns();
    const search = this.searchQuery().toLowerCase().trim();
    const priority = this.filterPriority();
    const state = this.filterState();

    if (!search && !priority && !state) {
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
        const matchesState = !state || task.state === state;
        return matchesSearch && matchesPriority && matchesState;
      }),
    }));
  });

  /** Connected drop lists: allow drop to any column (not restricted to adjacent) */
  connectedDropLists = computed(() => {
    const cols = this.columns();
    return cols.map((col, idx) => {
      return cols
        .filter((_, i) => i !== idx)
        .map(c => `drop-list-${c.id}`);
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
      ticketless: this.taskService.getTicketlessTasks().pipe(catchError(() => of([] as TicketlessTask[]))),
      agents: this.userSquadronService.getMySquadron().pipe(catchError(() => of([] as UserAgentConfig[]))),
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
        const ticketless: TicketlessTask[] = results.ticketless ?? [];
        const agents: UserAgentConfig[] = results.agents ?? [];

        this.ticketlessTasks.set(ticketless);
        this.availableAgents.set(agents.filter(a => a.enabled));

        // Build project map
        const pMap: Record<string, Project> = {};
        projectList.forEach(p => pMap[p.id] = p);
        this.projectMap.set(pMap);
        this.projects.set(projectList);
        this.projectSummaries.set(summaries);
        this.rawTasksByState.set(tasksByState);

        // Auto-select first project if none selected (no "All Projects")
        if (!this.selectedProjectId() && projectList.length > 0) {
          this.selectedProjectId.set(projectList[0].id);
          this.loadProjectMappings(projectList[0].id);
        } else if (this.selectedProjectId()) {
          // Reload mappings for current selection
          this.loadProjectMappings(this.selectedProjectId());
        }

        // Build columns
        this.rebuildColumns();
        this.loading.set(false);

        // Load agent sessions and conversation history for tasks
        this.loadAgentSessions();
        this.loadConversationCounts();
      },
      error: () => {
        this.rawTasksByState.set({});
        this.rebuildColumns();
        this.loading.set(false);
      },
    });
  }

  /** Switch to a specific project view */
  selectProject(projectId: string): void {
    if (!projectId) return; // No "all projects" allowed
    this.selectedProjectId.set(projectId);
    this.loadProjectMappings(projectId);
  }

  private loadProjectMappings(projectId: string): void {
    this.projectService.getWorkflowMappings(projectId).pipe(
      catchError(() => of([] as WorkflowMapping[])),
    ).subscribe(mappings => {
      this.workflowMappings.set(mappings);
      this.rebuildColumns();
    });
  }

  /** Column definitions for the 9-column board — one column per TaskState. */
  static readonly COLUMN_DEFS: { id: string; state: TaskState; label: string; color: string; icon: string }[] = [
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

  /** Rebuild columns based on current project selection and raw data */
  private rebuildColumns(): void {
    const tasksByState = this.rawTasksByState();
    const pMap = this.projectMap();
    const selectedId = this.selectedProjectId();
    const mappings = this.workflowMappings();
    const convos = this.taskConversations();

    const enrichTask = (task: Task): BoardTask => {
      const bt: BoardTask = {
        ...task,
        projectName: pMap[task.projectId]?.name,
        conversationCount: convos[task.id]?.length ?? 0,
        conversations: convos[task.id] ?? [],
      };
      // Resolve external status from workflow mappings
      const mapping = mappings.find(m => m.internalState === task.state);
      if (mapping) {
        bt.mappedExternalStatus = mapping.externalStatus;
      }
      return bt;
    };

    // Always filter tasks to selected project
    const filterByProject = (tasks: Task[]): Task[] => {
      if (!selectedId) return tasks;
      return tasks.filter(t => t.projectId === selectedId);
    };

    const cols = TaskBoardComponent.COLUMN_DEFS.map(def => {
      const allTasks = filterByProject(tasksByState[def.state] ?? []);
      const mapping = mappings.find(m => m.internalState === def.state);
      return {
        id: def.id,
        label: def.label,
        color: def.color,
        icon: def.icon,
        states: [def.state],
        tasks: allTasks.map(enrichTask),
        externalStatus: mapping?.externalStatus,
      } as BoardColumn;
    });

    this.columns.set(cols);
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

  /** Load conversation summaries for all tasks to show history count */
  private loadConversationCounts(): void {
    const allTaskIds = this.columns().flatMap(c => c.tasks.map(t => t.id));
    allTaskIds.forEach(taskId => {
      const sub = this.agentService.getConversationSummaries(taskId).pipe(
        catchError(() => of([] as ConversationSummary[])),
      ).subscribe(summaries => {
        const convos = { ...this.taskConversations() };
        convos[taskId] = summaries;
        this.taskConversations.set(convos);
        // Update the task in columns
        this.updateTaskConversations(taskId, summaries);
      });
      this.subscriptions.push(sub);
    });
  }

  private updateTaskConversations(taskId: string, summaries: ConversationSummary[]): void {
    const cols = this.columns().map(col => ({
      ...col,
      tasks: col.tasks.map(t => {
        if (t.id === taskId) {
          return { ...t, conversationCount: summaries.length, conversations: summaries };
        }
        return t;
      }),
    }));
    this.columns.set(cols);
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

  /** Toggle the agent history panel for a task */
  toggleHistoryPanel(taskId: string, event: Event): void {
    event.stopPropagation();
    if (this.expandedHistoryTaskId() === taskId) {
      this.expandedHistoryTaskId.set(null);
    } else {
      this.expandedHistoryTaskId.set(taskId);
      // Load full messages if we have an agent session
      this.loadTaskMessages(taskId);
    }
  }

  /** Load full messages for a task's agent session */
  private loadTaskMessages(taskId: string): void {
    const session = this.agentSessions()[taskId];
    if (session) {
      this.loadingMessages.set(taskId);
      this.agentService.getMessages(session.id).pipe(
        catchError(() => of([] as AgentMessage[])),
      ).subscribe(messages => {
        const msgs = { ...this.taskMessages() };
        msgs[taskId] = messages;
        this.taskMessages.set(msgs);
        this.loadingMessages.set(null);
      });
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

  /** Get recommended agent for a target state */
  getSuggestedAgentForState(state: TaskState): string {
    return STATE_AGENT_MAP[state] ?? 'PLANNING';
  }

  /** Check if a state has a recommended agent */
  hasAgentForState(state: TaskState): boolean {
    return state in STATE_AGENT_MAP;
  }

  /** Readable agent label */
  getAgentLabel(agentType: string): string {
    const at = AGENT_TYPES.find(a => a.value === agentType);
    return at ? this.translate.instant(at.label) : agentType;
  }

  /** Get message role display label */
  getMessageRoleLabel(role: string): string {
    switch (role) {
      case 'USER': return this.translate.instant('tasks.board.history.roleUser');
      case 'AGENT': return this.translate.instant('tasks.board.history.roleAgent');
      case 'SYSTEM': return this.translate.instant('tasks.board.history.roleSystem');
      default: return role;
    }
  }

  /** Get CSS class for a message role */
  getMessageRoleClass(role: string): string {
    return `history__msg--${role.toLowerCase()}`;
  }

  /** Get CSS class for a conversation status */
  getConversationStatusClass(status: string): string {
    return `history__conv-status--${status.toLowerCase()}`;
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
      const fromState = task.state;
      const targetState = targetColumn.states[0];

      // Move the card visually
      transferArrayItem(
        event.previousContainer.data,
        event.container.data,
        event.previousIndex,
        event.currentIndex,
      );

      // Perform the transition
      this.taskService.transitionTask(task.id, targetState).subscribe({
        next: () => {
          task.state = targetState;

          // Always show the drop modal for cross-column moves
          const suggestedAgent = STATE_AGENT_MAP[targetState] ?? 'PLANNING';
          this.dropModal.set({
            task: { ...task },
            fromState,
            toState: targetState,
            suggestedAgent,
            instructions: '',
          });
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

  // --- Drop modal (agent delegation after drag-drop) ---

  dismissDropModal(): void {
    this.dropModal.set(null);
  }

  updateDropModalInstructions(value: string): void {
    const s = this.dropModal();
    if (s) {
      this.dropModal.set({ ...s, instructions: value });
    }
  }

  updateDropModalAgent(value: string): void {
    const s = this.dropModal();
    if (s) {
      this.dropModal.set({ ...s, suggestedAgent: value });
    }
  }

  acceptDropModal(): void {
    const s = this.dropModal();
    if (!s) return;

    const request: DelegateTaskRequest = {
      agentType: s.suggestedAgent,
      instructions: s.instructions || undefined,
      targetState: s.toState,
    };

    this.dropModalDelegating.set(true);
    this.taskService.delegateToAgent(s.task.id, request).subscribe({
      next: () => {
        this.dropModalDelegating.set(false);
        this.dropModal.set(null);
        this.loadData();
      },
      error: () => {
        this.dropModalDelegating.set(false);
      },
    });
  }

  // --- View mode toggle ---

  setViewMode(mode: 'board' | 'list'): void {
    this.viewMode.set(mode);
  }

  // --- Refresh ---

  refresh(): void {
    this.loadData();
  }

  // --- Ticketless tasks ---

  openTicketlessDialog(): void {
    this.showTicketlessDialog.set(true);
    this.ticketlessTitle.set('');
    this.ticketlessPrompt.set('');
    this.ticketlessBranch.set('');
    this.ticketlessCreateBranch.set(false);
    this.ticketlessAgentMode.set('BUILD');
    this.ticketlessAgentConfigId.set(this.availableAgents()[0]?.id ?? '');
    // Default to selected project if it has a git remote, otherwise empty (user must pick)
    const selected = this.selectedProject();
    this.ticketlessProjectId.set(selected?.gitConnectionId ? selected.id : '');
    this.applyBranchTemplate();
  }

  /** Re-apply the branch naming template when project or title changes */
  onTicketlessProjectChange(projectId: string): void {
    this.ticketlessProjectId.set(projectId);
    this.applyBranchTemplate();
  }

  onTicketlessTitleChange(title: string): void {
    this.ticketlessTitle.set(title);
    this.applyBranchTemplate();
  }

  private applyBranchTemplate(): void {
    const projectId = this.ticketlessProjectId();
    if (!projectId) return;

    const project = this.projectMap()[projectId];
    if (!project?.branchNamingTemplate) return;

    const title = this.ticketlessTitle().trim();
    const slug = title
      ? title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').substring(0, 50)
      : '';

    const branch = project.branchNamingTemplate
      .replace('{strategy}', 'feature')
      .replace('{ticket}', 'ticketless')
      .replace('{description}', slug || 'task')
      .replace('{type}', 'feat')
      .replace('{user}', '');

    this.ticketlessBranch.set(branch);
  }

  closeTicketlessDialog(): void {
    this.showTicketlessDialog.set(false);
  }

  createTicketlessTask(): void {
    const title = this.ticketlessTitle().trim();
    const prompt = this.ticketlessPrompt().trim();
    const agentConfigId = this.ticketlessAgentConfigId();
    const projectId = this.ticketlessProjectId();
    if (!title || !prompt || !agentConfigId || !projectId) return;

    const request: CreateTicketlessTaskRequest = {
      title,
      prompt,
      branchName: this.ticketlessBranch().trim() || undefined,
      createBranch: this.ticketlessCreateBranch(),
      agentMode: this.ticketlessAgentMode(),
      agentConfigId,
      projectId,
    };

    this.creatingTicketless.set(true);
    this.taskService.createTicketlessTask(request).subscribe({
      next: () => {
        this.creatingTicketless.set(false);
        this.showTicketlessDialog.set(false);
        this.loadData();
      },
      error: () => {
        this.creatingTicketless.set(false);
      },
    });
  }

  getTicketlessStatusLabel(status?: string): string {
    if (!status) return '';
    return this.translate.instant(`tasks.board.ticketless.status.${status}`);
  }

  ticketlessStatusColor(status?: string): string {
    switch (status) {
      case 'CREATED': return '#94A3B8';
      case 'PLANNING': return '#818CF8';
      case 'BUILDING': return '#06B6D4';
      case 'COMPLETED': return '#10B981';
      case 'FAILED': return '#DC2626';
      default: return '#94A3B8';
    }
  }
}
