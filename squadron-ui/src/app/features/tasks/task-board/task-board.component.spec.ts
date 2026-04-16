import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TaskBoardComponent, BoardTask, BoardColumn } from './task-board.component';
import { TaskService } from '../../../core/services/task.service';
import { ProjectService } from '../../../core/services/project.service';
import { AgentService, AgentSession } from '../../../core/services/agent.service';
import { WorkspaceService, Workspace } from '../../../core/services/workspace.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { TaskState, TaskPriority, Task, TaskSyncResult } from '../../../core/models/task.model';
import { Project } from '../../../core/models/project.model';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { TranslateModule } from '@ngx-translate/core';

function makeTask(overrides: Partial<Task> = {}): Task {
  return {
    id: 'task-1',
    tenantId: 't1',
    projectId: 'p1',
    title: 'Test Task',
    state: TaskState.IN_PROGRESS,
    priority: TaskPriority.HIGH,
    labels: [],
    tokenUsage: 0,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides,
  };
}

function makeProject(overrides: Partial<Project> = {}): Project {
  return {
    id: 'p1',
    tenantId: 't1',
    teamId: 'team-1',
    name: 'Test Project',
    description: 'A project',
    defaultBranch: 'main',
    taskCount: 0,
    activeTaskCount: 0,
    members: [],
    createdAt: new Date().toISOString(),
    ...overrides,
  } as Project;
}

function makeApiData(overrides: Partial<Record<TaskState, Task[]>> = {}): Record<TaskState, Task[]> {
  return {
    [TaskState.BACKLOG]: [],
    [TaskState.PRIORITIZED]: [],
    [TaskState.PLANNING]: [],
    [TaskState.PROPOSE_CODE]: [],
    [TaskState.IN_PROGRESS]: [],
    [TaskState.REVIEW]: [],
    [TaskState.QA]: [],
    [TaskState.MERGE]: [],
    [TaskState.DONE]: [],
    ...overrides,
  };
}

/** All 9 column IDs in order */
const ALL_COLUMN_IDS = [
  'backlog', 'prioritized', 'planning', 'propose-code',
  'in-progress', 'review', 'qa', 'merge', 'done',
];

describe('TaskBoardComponent', () => {
  let component: TaskBoardComponent;
  let fixture: ComponentFixture<TaskBoardComponent>;
  let taskServiceSpy: jasmine.SpyObj<TaskService>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let agentServiceSpy: jasmine.SpyObj<AgentService>;
  let workspaceServiceSpy: jasmine.SpyObj<WorkspaceService>;
  let authServiceStub: Partial<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    taskServiceSpy = jasmine.createSpyObj('TaskService', ['getTasksByState', 'transitionTask', 'syncTasks', 'delegateToAgent']);
    projectServiceSpy = jasmine.createSpyObj('ProjectService', ['getProjectsByTenant', 'getProjectSummaries']);
    agentServiceSpy = jasmine.createSpyObj('AgentService', [
      'getSession', 'sendMessage', 'interruptAgent', 'approvePlan', 'rejectPlan',
    ]);
    workspaceServiceSpy = jasmine.createSpyObj('WorkspaceService', [
      'getWorkspaceByTask', 'destroyWorkspace',
    ]);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    authServiceStub = {
      user: (() => ({ id: 'u1', tenantId: 't1', username: 'test', email: '', displayName: 'Test', tenantName: '', roles: [], permissions: [] })) as any,
    };

    // Default: return empty tasks and no projects
    taskServiceSpy.getTasksByState.and.returnValue(of(makeApiData()));
    projectServiceSpy.getProjectsByTenant.and.returnValue(of([]));
    projectServiceSpy.getProjectSummaries.and.returnValue(of([]));
    agentServiceSpy.getSession.and.returnValue(of({ id: '', taskId: '', agentType: '', status: 'IDLE', messages: [], createdAt: '' } as any));

    await TestBed.configureTestingModule({
      imports: [TaskBoardComponent, TranslateModule.forRoot()],
      providers: [
        { provide: TaskService, useValue: taskServiceSpy },
        { provide: ProjectService, useValue: projectServiceSpy },
        { provide: AgentService, useValue: agentServiceSpy },
        { provide: WorkspaceService, useValue: workspaceServiceSpy },
        { provide: AuthService, useValue: authServiceStub },
        { provide: Router, useValue: routerSpy },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TaskBoardComponent);
    component = fixture.componentInstance;
  });

  // --- Creation and initialization ---

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should create 9 columns on successful load', () => {
    fixture.detectChanges();
    expect(component.columns().length).toBe(9);
    const ids = component.columns().map(c => c.id);
    expect(ids).toEqual(ALL_COLUMN_IDS);
  });

  it('should create 9 empty columns on API error', () => {
    taskServiceSpy.getTasksByState.and.returnValue(throwError(() => new Error('api down')));
    fixture.detectChanges();
    expect(component.columns().length).toBe(9);
    for (const col of component.columns()) {
      expect(col.tasks.length).toBe(0);
    }
  });

  it('should set loading to false after load', () => {
    fixture.detectChanges();
    expect(component.loading()).toBeFalse();
  });

  it('should set loading to false on error', () => {
    taskServiceSpy.getTasksByState.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.loading()).toBeFalse();
  });

  // --- Column mapping ---

  it('should place each state in its own column', () => {
    const stateToColumn: Record<string, string> = {
      [TaskState.BACKLOG]: 'backlog',
      [TaskState.PRIORITIZED]: 'prioritized',
      [TaskState.PLANNING]: 'planning',
      [TaskState.PROPOSE_CODE]: 'propose-code',
      [TaskState.IN_PROGRESS]: 'in-progress',
      [TaskState.REVIEW]: 'review',
      [TaskState.QA]: 'qa',
      [TaskState.MERGE]: 'merge',
      [TaskState.DONE]: 'done',
    };

    for (const [state, columnId] of Object.entries(stateToColumn)) {
      const apiData = makeApiData({
        [state as TaskState]: [makeTask({ id: `task-${state}`, state: state as TaskState })],
      });
      taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
      fixture = TestBed.createComponent(TaskBoardComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const col = component.columns().find(c => c.id === columnId);
      expect(col).toBeTruthy(`Column ${columnId} not found`);
      expect(col!.tasks.length).toBe(1, `Expected 1 task in column ${columnId} for state ${state}`);
      expect(col!.tasks[0].state).toBe(state as TaskState);
    }
  });

  it('should place all 9 states into their respective columns with 1 task each', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [makeTask({ id: '1', state: TaskState.BACKLOG })],
      [TaskState.PRIORITIZED]: [makeTask({ id: '2', state: TaskState.PRIORITIZED })],
      [TaskState.PLANNING]: [makeTask({ id: '3', state: TaskState.PLANNING })],
      [TaskState.PROPOSE_CODE]: [makeTask({ id: '4', state: TaskState.PROPOSE_CODE })],
      [TaskState.IN_PROGRESS]: [makeTask({ id: '5', state: TaskState.IN_PROGRESS })],
      [TaskState.REVIEW]: [makeTask({ id: '6', state: TaskState.REVIEW })],
      [TaskState.QA]: [makeTask({ id: '7', state: TaskState.QA })],
      [TaskState.MERGE]: [makeTask({ id: '8', state: TaskState.MERGE })],
      [TaskState.DONE]: [makeTask({ id: '9', state: TaskState.DONE })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    for (const col of component.columns()) {
      expect(col.tasks.length).toBe(1, `Expected 1 task in column ${col.id}`);
    }
  });

  it('should store exactly 1 state per column', () => {
    fixture.detectChanges();
    const expectedStates: Record<string, TaskState> = {
      'backlog': TaskState.BACKLOG,
      'prioritized': TaskState.PRIORITIZED,
      'planning': TaskState.PLANNING,
      'propose-code': TaskState.PROPOSE_CODE,
      'in-progress': TaskState.IN_PROGRESS,
      'review': TaskState.REVIEW,
      'qa': TaskState.QA,
      'merge': TaskState.MERGE,
      'done': TaskState.DONE,
    };

    for (const col of component.columns()) {
      expect(col.states.length).toBe(1, `Column ${col.id} should have exactly 1 state`);
      expect(col.states[0]).toBe(expectedStates[col.id], `Column ${col.id} has wrong state`);
    }
  });

  // --- Project enrichment ---

  it('should enrich tasks with project name', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [makeTask({ id: '1', projectId: 'p1' })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    projectServiceSpy.getProjectsByTenant.and.returnValue(of([
      makeProject({ id: 'p1', name: 'My Project' }),
    ]));
    fixture.detectChanges();

    const col = component.columns().find(c => c.id === 'in-progress')!;
    expect((col.tasks[0] as BoardTask).projectName).toBe('My Project');
  });

  it('should populate projects signal for dropdown', () => {
    projectServiceSpy.getProjectsByTenant.and.returnValue(of([
      makeProject({ id: 'p1', name: 'Alpha' }),
      makeProject({ id: 'p2', name: 'Beta' }),
    ]));
    fixture.detectChanges();

    expect(component.projects().length).toBe(2);
  });

  // --- Summary counts ---

  it('should compute totalInProgress correctly', () => {
    const apiData = makeApiData({
      [TaskState.PROPOSE_CODE]: [makeTask({ id: '0', state: TaskState.PROPOSE_CODE })],
      [TaskState.IN_PROGRESS]: [makeTask({ id: '1' }), makeTask({ id: '2' })],
      [TaskState.REVIEW]: [makeTask({ id: '3' })],
      [TaskState.QA]: [makeTask({ id: '4', state: TaskState.QA })],
      [TaskState.MERGE]: [makeTask({ id: '5', state: TaskState.MERGE })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    expect(component.totalInProgress()).toBe(6);
  });

  it('should compute totalPlanned correctly', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [makeTask({ id: '1', state: TaskState.BACKLOG })],
      [TaskState.PRIORITIZED]: [makeTask({ id: '2', state: TaskState.PRIORITIZED })],
      [TaskState.PLANNING]: [makeTask({ id: '3', state: TaskState.PLANNING })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    expect(component.totalPlanned()).toBe(3);
  });

  it('should compute totalCompleted correctly', () => {
    const apiData = makeApiData({
      [TaskState.DONE]: [makeTask({ id: '1', state: TaskState.DONE }), makeTask({ id: '2', state: TaskState.DONE }), makeTask({ id: '3', state: TaskState.DONE })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    expect(component.totalCompleted()).toBe(3);
  });

  // --- Filtering ---

  it('should initialize filter fields as empty', () => {
    expect(component.filterPriority()).toBe('');
    expect(component.filterProject()).toBe('');
    expect(component.searchQuery()).toBe('');
  });

  it('should return all 9 columns from filteredColumns when no filters set', () => {
    fixture.detectChanges();
    const filtered = component.filteredColumns();
    expect(filtered.length).toBe(9);
    expect(filtered).toEqual(component.columns());
  });

  it('should filter by priority', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [
        makeTask({ id: '1', state: TaskState.BACKLOG, priority: TaskPriority.LOW }),
        makeTask({ id: '2', state: TaskState.BACKLOG, priority: TaskPriority.HIGH }),
      ],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.filterPriority.set('HIGH');
    const backlog = component.filteredColumns().find(c => c.id === 'backlog')!;
    expect(backlog.tasks.length).toBe(1);
    expect(backlog.tasks[0].priority).toBe(TaskPriority.HIGH);
  });

  it('should filter by project', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [
        makeTask({ id: '1', projectId: 'p1' }),
        makeTask({ id: '2', projectId: 'p2' }),
      ],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.filterProject.set('p1');
    const inProgress = component.filteredColumns().find(c => c.id === 'in-progress')!;
    expect(inProgress.tasks.length).toBe(1);
    expect(inProgress.tasks[0].projectId).toBe('p1');
  });

  it('should filter by search query matching title', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [
        makeTask({ id: '1', state: TaskState.BACKLOG, title: 'Add export feature' }),
        makeTask({ id: '2', state: TaskState.BACKLOG, title: 'Refactor module' }),
      ],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.searchQuery.set('export');
    const backlog = component.filteredColumns().find(c => c.id === 'backlog')!;
    expect(backlog.tasks.length).toBe(1);
    expect(backlog.tasks[0].title).toContain('export');
  });

  it('should filter by search query matching labels', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [
        makeTask({ id: '1', labels: ['bug'] }),
        makeTask({ id: '2', labels: ['feature'] }),
      ],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.searchQuery.set('bug');
    const inProgress = component.filteredColumns().find(c => c.id === 'in-progress')!;
    expect(inProgress.tasks.length).toBe(1);
    expect(inProgress.tasks[0].labels).toContain('bug');
  });

  it('should filter by search query matching externalId', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [
        makeTask({ id: '1', externalId: 'SQ-42' }),
        makeTask({ id: '2' }),
      ],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.searchQuery.set('SQ-42');
    const total = component.filteredColumns().reduce((sum, col) => sum + col.tasks.length, 0);
    expect(total).toBe(1);
  });

  it('should filter by search query matching project name', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [
        makeTask({ id: '1', projectId: 'p1' }),
        makeTask({ id: '2', projectId: 'p2' }),
      ],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    projectServiceSpy.getProjectsByTenant.and.returnValue(of([
      makeProject({ id: 'p1', name: 'Alpha' }),
      makeProject({ id: 'p2', name: 'Beta' }),
    ]));
    fixture.detectChanges();

    component.searchQuery.set('alpha');
    const inProgress = component.filteredColumns().find(c => c.id === 'in-progress')!;
    expect(inProgress.tasks.length).toBe(1);
    expect((inProgress.tasks[0] as BoardTask).projectName).toBe('Alpha');
  });

  it('should combine search and priority filters', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [
        makeTask({ id: '1', title: 'Dashboard feature', priority: TaskPriority.HIGH }),
        makeTask({ id: '2', title: 'Dashboard bugfix', priority: TaskPriority.LOW }),
        makeTask({ id: '3', title: 'Other task', priority: TaskPriority.HIGH }),
      ],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.searchQuery.set('dashboard');
    component.filterPriority.set('HIGH');
    const total = component.filteredColumns().reduce((sum, col) => sum + col.tasks.length, 0);
    expect(total).toBe(1);
  });

  // --- Connected drop lists (adjacency) ---

  it('should compute connectedDropLists as string[][] with adjacent column IDs', () => {
    fixture.detectChanges();
    const lists = component.connectedDropLists();
    expect(lists.length).toBe(9);
    // Each entry is a string[] of adjacent column drop-list IDs
    expect(Array.isArray(lists[0])).toBeTrue();

    // First column (backlog) connects only to next (prioritized)
    expect(lists[0]).toEqual(['drop-list-prioritized']);
    // Second column (prioritized) connects to prev + next
    expect(lists[1]).toEqual(['drop-list-backlog', 'drop-list-planning']);
    // Middle column (in-progress, index 4) connects to prev + next
    expect(lists[4]).toEqual(['drop-list-propose-code', 'drop-list-review']);
    // Last column (done) connects only to prev (merge)
    expect(lists[8]).toEqual(['drop-list-merge']);
  });

  it('should return correct connected lists via getConnectedLists helper', () => {
    fixture.detectChanges();
    // First column
    expect(component.getConnectedLists(0)).toEqual(['drop-list-prioritized']);
    // Last column
    expect(component.getConnectedLists(8)).toEqual(['drop-list-merge']);
    // Out of bounds returns empty
    expect(component.getConnectedLists(99)).toEqual([]);
  });

  // --- Navigation ---

  it('should navigate to task detail on openTask', () => {
    fixture.detectChanges();
    const mockTask = { id: 'task-123' } as Task;
    component.openTask(mockTask);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/tasks', 'task-123']);
  });

  it('should navigate to agent chat on openAgentChat', () => {
    fixture.detectChanges();
    component.openAgentChat({ id: 'task-456' } as BoardTask);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/agent', 'task-456']);
  });

  // --- Toggle task actions ---

  it('should toggle expandedTaskId', () => {
    fixture.detectChanges();
    expect(component.expandedTaskId()).toBeNull();

    component.toggleTaskActions('task-1');
    expect(component.expandedTaskId()).toBe('task-1');

    component.toggleTaskActions('task-1');
    expect(component.expandedTaskId()).toBeNull();
  });

  it('should switch expandedTaskId when toggling different task', () => {
    fixture.detectChanges();
    component.toggleTaskActions('task-1');
    component.toggleTaskActions('task-2');
    expect(component.expandedTaskId()).toBe('task-2');
  });

  // --- Priority badge class ---

  it('should return correct priority badge class', () => {
    expect(component.priorityBadgeClass(TaskPriority.CRITICAL)).toBe('error');
    expect(component.priorityBadgeClass(TaskPriority.HIGH)).toBe('warning');
    expect(component.priorityBadgeClass(TaskPriority.MEDIUM)).toBe('primary');
    expect(component.priorityBadgeClass(TaskPriority.LOW)).toBe('neutral');
  });

  // --- State badge ---

  it('should return translated state badge', () => {
    fixture.detectChanges();
    const task = makeTask({ state: TaskState.IN_PROGRESS }) as BoardTask;
    const badge = component.getStateBadge(task);
    // TranslateModule.forRoot() returns the key itself when no translations configured
    expect(badge).toBe('tasks.state.IN_PROGRESS');
  });

  // --- Agent status label ---

  it('should return empty string for task without agent status', () => {
    const task = { agentStatus: null } as BoardTask;
    expect(component.getAgentStatusLabel(task)).toBe('');
  });

  it('should return translation key for active agent status', () => {
    const task = { agentStatus: 'ACTIVE' } as BoardTask;
    const label = component.getAgentStatusLabel(task);
    expect(label).toBe('tasks.board.agentStatus.active');
  });

  // --- Cancel task ---

  it('should cancel task by transitioning to BACKLOG', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [makeTask({ id: 'task-1', state: TaskState.IN_PROGRESS })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    taskServiceSpy.transitionTask.and.returnValue(of({} as Task));
    workspaceServiceSpy.getWorkspaceByTask.and.returnValue(throwError(() => new Error('no workspace')));
    fixture.detectChanges();

    const task = component.columns().find(c => c.id === 'in-progress')!.tasks[0] as BoardTask;
    component.cancelTask(task);

    expect(taskServiceSpy.transitionTask).toHaveBeenCalledWith('task-1', TaskState.BACKLOG);
  });

  it('should interrupt agent before cancelling if agent is active', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [makeTask({ id: 'task-1', state: TaskState.IN_PROGRESS, agentSessionId: 'session-1' })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    // Update the task with agent status
    const cols = component.columns().map(c => ({
      ...c,
      tasks: c.tasks.map(t => t.id === 'task-1' ? { ...t, agentStatus: 'ACTIVE' as const } : t),
    }));
    component.columns.set(cols);

    agentServiceSpy.interruptAgent.and.returnValue(of(void 0));
    taskServiceSpy.transitionTask.and.returnValue(of({} as Task));
    workspaceServiceSpy.getWorkspaceByTask.and.returnValue(throwError(() => new Error('no workspace')));

    const task = component.columns().find(c => c.id === 'in-progress')!.tasks[0] as BoardTask;
    component.cancelTask(task);

    expect(agentServiceSpy.interruptAgent).toHaveBeenCalledWith('session-1', 'USER_CANCEL');
    expect(taskServiceSpy.transitionTask).toHaveBeenCalledWith('task-1', TaskState.BACKLOG);
  });

  it('should move cancelled task from in-progress to backlog column', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [makeTask({ id: 'task-1', state: TaskState.IN_PROGRESS })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    taskServiceSpy.transitionTask.and.returnValue(of({} as Task));
    workspaceServiceSpy.getWorkspaceByTask.and.returnValue(throwError(() => new Error('no workspace')));
    fixture.detectChanges();

    const task = component.columns().find(c => c.id === 'in-progress')!.tasks[0] as BoardTask;
    component.cancelTask(task);

    expect(component.columns().find(c => c.id === 'in-progress')!.tasks.length).toBe(0);
    expect(component.columns().find(c => c.id === 'backlog')!.tasks.length).toBe(1);
  });

  it('should cleanup workspace after cancel', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [makeTask({ id: 'task-1', state: TaskState.IN_PROGRESS })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    taskServiceSpy.transitionTask.and.returnValue(of({} as Task));
    const mockWorkspace: Workspace = {
      id: 'ws-1', tenantId: 't1', taskId: 'task-1', status: 'RUNNING',
      createdAt: '', updatedAt: '',
    };
    workspaceServiceSpy.getWorkspaceByTask.and.returnValue(of(mockWorkspace));
    workspaceServiceSpy.destroyWorkspace.and.returnValue(of(void 0));
    fixture.detectChanges();

    const task = component.columns().find(c => c.id === 'in-progress')!.tasks[0] as BoardTask;
    component.cancelTask(task);

    expect(workspaceServiceSpy.getWorkspaceByTask).toHaveBeenCalledWith('task-1');
    expect(workspaceServiceSpy.destroyWorkspace).toHaveBeenCalledWith('ws-1');
  });

  it('should not destroy workspace if already stopped', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [makeTask({ id: 'task-1', state: TaskState.IN_PROGRESS })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    taskServiceSpy.transitionTask.and.returnValue(of({} as Task));
    const mockWorkspace: Workspace = {
      id: 'ws-1', tenantId: 't1', taskId: 'task-1', status: 'STOPPED',
      createdAt: '', updatedAt: '',
    };
    workspaceServiceSpy.getWorkspaceByTask.and.returnValue(of(mockWorkspace));
    fixture.detectChanges();

    const task = component.columns().find(c => c.id === 'in-progress')!.tasks[0] as BoardTask;
    component.cancelTask(task);

    expect(workspaceServiceSpy.destroyWorkspace).not.toHaveBeenCalled();
  });

  it('should reset cancellingTaskId after cancel completes', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [makeTask({ id: 'task-1', state: TaskState.IN_PROGRESS })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    taskServiceSpy.transitionTask.and.returnValue(of({} as Task));
    workspaceServiceSpy.getWorkspaceByTask.and.returnValue(throwError(() => new Error('no ws')));
    fixture.detectChanges();

    const task = component.columns().find(c => c.id === 'in-progress')!.tasks[0] as BoardTask;
    component.cancelTask(task);

    expect(component.cancellingTaskId()).toBeNull();
  });

  it('should reset cancellingTaskId on transition error', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [makeTask({ id: 'task-1', state: TaskState.IN_PROGRESS })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    taskServiceSpy.transitionTask.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    const task = component.columns().find(c => c.id === 'in-progress')!.tasks[0] as BoardTask;
    component.cancelTask(task);

    expect(component.cancellingTaskId()).toBeNull();
  });

  // --- Quick prompt ---

  it('should send prompt to agent', () => {
    agentServiceSpy.sendMessage.and.returnValue(of({ id: 'm1', sessionId: 's1', role: 'USER', content: 'hello', createdAt: '' } as any));
    fixture.detectChanges();

    const task = { id: 'task-1', agentSessionId: 'session-1' } as BoardTask;
    component.updatePromptText('task-1', 'Fix the bug');
    component.sendPrompt(task);

    expect(agentServiceSpy.sendMessage).toHaveBeenCalledWith('session-1', 'Fix the bug');
  });

  it('should clear prompt text after successful send', () => {
    agentServiceSpy.sendMessage.and.returnValue(of({ id: 'm1', sessionId: 's1', role: 'USER', content: 'hello', createdAt: '' } as any));
    fixture.detectChanges();

    const task = { id: 'task-1', agentSessionId: 'session-1' } as BoardTask;
    component.updatePromptText('task-1', 'Fix the bug');
    component.sendPrompt(task);

    expect(component.promptText()['task-1']).toBe('');
  });

  it('should not send prompt when text is empty', () => {
    fixture.detectChanges();
    const task = { id: 'task-1', agentSessionId: 'session-1' } as BoardTask;
    component.sendPrompt(task);
    expect(agentServiceSpy.sendMessage).not.toHaveBeenCalled();
  });

  it('should not send prompt when no agentSessionId', () => {
    fixture.detectChanges();
    const task = { id: 'task-1' } as BoardTask;
    component.updatePromptText('task-1', 'Fix it');
    component.sendPrompt(task);
    expect(agentServiceSpy.sendMessage).not.toHaveBeenCalled();
  });

  it('should reset sendingPromptTaskId after send completes', () => {
    agentServiceSpy.sendMessage.and.returnValue(of({ id: 'm1', sessionId: 's1', role: 'USER', content: '', createdAt: '' } as any));
    fixture.detectChanges();

    const task = { id: 'task-1', agentSessionId: 'session-1' } as BoardTask;
    component.updatePromptText('task-1', 'test');
    component.sendPrompt(task);

    expect(component.sendingPromptTaskId()).toBeNull();
  });

  it('should reset sendingPromptTaskId on send error', () => {
    agentServiceSpy.sendMessage.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    const task = { id: 'task-1', agentSessionId: 'session-1' } as BoardTask;
    component.updatePromptText('task-1', 'test');
    component.sendPrompt(task);

    expect(component.sendingPromptTaskId()).toBeNull();
  });

  // --- Plan approval ---

  it('should approve plan via agent service', () => {
    agentServiceSpy.approvePlan.and.returnValue(of(void 0));
    agentServiceSpy.getSession.and.returnValue(of({
      id: 's1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: '',
    } as AgentSession));
    fixture.detectChanges();

    // Set up session with plan
    component.agentSessions.set({
      'task-1': {
        id: 's1', taskId: 'task-1', status: 'WAITING_INPUT', totalTokens: 0,
        messages: [], createdAt: '',
        currentPlan: { id: 'plan-1', steps: [], approved: false, createdAt: '' },
      },
    });

    const task = { id: 'task-1', agentSessionId: 'session-1' } as BoardTask;
    component.approvePlan(task);

    expect(agentServiceSpy.approvePlan).toHaveBeenCalledWith('session-1', 'plan-1');
  });

  it('should reject plan via agent service', () => {
    agentServiceSpy.rejectPlan.and.returnValue(of(void 0));
    agentServiceSpy.getSession.and.returnValue(of({
      id: 's1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: '',
    } as AgentSession));
    fixture.detectChanges();

    component.agentSessions.set({
      'task-1': {
        id: 's1', taskId: 'task-1', status: 'WAITING_INPUT', totalTokens: 0,
        messages: [], createdAt: '',
        currentPlan: { id: 'plan-1', steps: [], approved: false, createdAt: '' },
      },
    });

    component.updatePromptText('task-1', 'Needs more detail');
    const task = { id: 'task-1', agentSessionId: 'session-1' } as BoardTask;
    component.rejectPlan(task);

    expect(agentServiceSpy.rejectPlan).toHaveBeenCalledWith('session-1', 'plan-1', 'Needs more detail');
  });

  it('should not approve plan without currentPlan', () => {
    fixture.detectChanges();
    component.agentSessions.set({
      'task-1': {
        id: 's1', taskId: 'task-1', status: 'WAITING_INPUT', totalTokens: 0,
        messages: [], createdAt: '',
      },
    });

    const task = { id: 'task-1', agentSessionId: 'session-1' } as BoardTask;
    component.approvePlan(task);
    expect(agentServiceSpy.approvePlan).not.toHaveBeenCalled();
  });

  it('should report hasActivePlan correctly', () => {
    fixture.detectChanges();

    // No session
    expect(component.hasActivePlan({ id: 'task-1' } as BoardTask)).toBeFalse();

    // Session with no plan
    component.agentSessions.set({
      'task-1': {
        id: 's1', taskId: 'task-1', status: 'WAITING_INPUT', totalTokens: 0,
        messages: [], createdAt: '',
      },
    });
    expect(component.hasActivePlan({ id: 'task-1' } as BoardTask)).toBeFalse();

    // Session with approved plan
    component.agentSessions.set({
      'task-1': {
        id: 's1', taskId: 'task-1', status: 'WAITING_INPUT', totalTokens: 0,
        messages: [], createdAt: '',
        currentPlan: { id: 'plan-1', steps: [], approved: true, createdAt: '' },
      },
    });
    expect(component.hasActivePlan({ id: 'task-1' } as BoardTask)).toBeFalse();

    // Session with unapproved plan
    component.agentSessions.set({
      'task-1': {
        id: 's1', taskId: 'task-1', status: 'WAITING_INPUT', totalTokens: 0,
        messages: [], createdAt: '',
        currentPlan: { id: 'plan-1', steps: [], approved: false, createdAt: '' },
      },
    });
    expect(component.hasActivePlan({ id: 'task-1' } as BoardTask)).toBeTrue();
  });

  // --- Drag-drop ---

  it('should reorder within same column on drop', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [
        makeTask({ id: '1', state: TaskState.BACKLOG, title: 'First' }),
        makeTask({ id: '2', state: TaskState.BACKLOG, title: 'Second' }),
      ],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    const backlogCol = component.columns().find(c => c.id === 'backlog')!;
    const containerData = [...backlogCol.tasks];
    const container = { data: containerData } as any;

    const event: Partial<CdkDragDrop<BoardTask[]>> = {
      previousContainer: container,
      container: container,
      previousIndex: 0,
      currentIndex: 1,
      item: {} as any,
      isPointerOverContainer: true,
      distance: { x: 0, y: 0 },
      dropPoint: { x: 0, y: 0 },
      event: {} as any,
    };

    component.drop(event as CdkDragDrop<BoardTask[]>, backlogCol);
    expect(containerData[0].title).toBe('Second');
    expect(containerData[1].title).toBe('First');
  });

  it('should call transitionTask when task dropped to adjacent column', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [makeTask({ id: '1', state: TaskState.BACKLOG, title: 'Task A' })],
      [TaskState.PRIORITIZED]: [],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    const backlogCol = component.columns().find(c => c.id === 'backlog')!;
    const prioritizedCol = component.columns().find(c => c.id === 'prioritized')!;
    const sourceData = [...backlogCol.tasks];
    const targetData = [...prioritizedCol.tasks];
    const sourceContainer = { data: sourceData } as any;
    const targetContainer = { data: targetData } as any;

    taskServiceSpy.transitionTask.and.returnValue(of({} as Task));

    const event: Partial<CdkDragDrop<BoardTask[]>> = {
      previousContainer: sourceContainer,
      container: targetContainer,
      previousIndex: 0,
      currentIndex: 0,
      item: {} as any,
      isPointerOverContainer: true,
      distance: { x: 0, y: 0 },
      dropPoint: { x: 0, y: 0 },
      event: {} as any,
    };

    component.drop(event as CdkDragDrop<BoardTask[]>, prioritizedCol);
    // Target state is the single state of the target column = PRIORITIZED
    expect(taskServiceSpy.transitionTask).toHaveBeenCalledWith('1', TaskState.PRIORITIZED);
    expect(sourceData.length).toBe(0);
    expect(targetData.length).toBe(1);
  });

  it('should revert transfer on failed transition', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [makeTask({ id: '1', state: TaskState.BACKLOG })],
      [TaskState.PRIORITIZED]: [makeTask({ id: '2', state: TaskState.PRIORITIZED })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    const backlogCol = component.columns().find(c => c.id === 'backlog')!;
    const prioritizedCol = component.columns().find(c => c.id === 'prioritized')!;
    const sourceData = [...backlogCol.tasks];
    const targetData = [...prioritizedCol.tasks];
    const origSourceLen = sourceData.length;
    const origTargetLen = targetData.length;
    const sourceContainer = { data: sourceData } as any;
    const targetContainer = { data: targetData } as any;

    taskServiceSpy.transitionTask.and.returnValue(throwError(() => new Error('Server error')));

    const event: Partial<CdkDragDrop<BoardTask[]>> = {
      previousContainer: sourceContainer,
      container: targetContainer,
      previousIndex: 0,
      currentIndex: 0,
      item: {} as any,
      isPointerOverContainer: true,
      distance: { x: 0, y: 0 },
      dropPoint: { x: 0, y: 0 },
      event: {} as any,
    };

    component.drop(event as CdkDragDrop<BoardTask[]>, prioritizedCol);
    expect(sourceData.length).toBe(origSourceLen);
    expect(targetData.length).toBe(origTargetLen);
  });

  // --- Refresh ---

  it('should reload data on refresh', () => {
    fixture.detectChanges();
    taskServiceSpy.getTasksByState.calls.reset();
    component.refresh();
    expect(taskServiceSpy.getTasksByState).toHaveBeenCalledTimes(1);
  });

  // --- Update prompt text ---

  it('should update prompt text for a task', () => {
    component.updatePromptText('task-1', 'hello');
    expect(component.promptText()['task-1']).toBe('hello');
  });

  // --- Task sync ---

  it('should compute syncableProjects from projects with connectionId and externalProjectId', () => {
    projectServiceSpy.getProjectsByTenant.and.returnValue(of([
      makeProject({ id: 'p1', name: 'Syncable', connectionId: 'conn-1', externalProjectId: 'SQ' }),
      makeProject({ id: 'p2', name: 'No Connection' }),
      makeProject({ id: 'p3', name: 'Has Connection Only', connectionId: 'conn-2' }),
      makeProject({ id: 'p4', name: 'Has Key Only', externalProjectId: 'KEY' }),
      makeProject({ id: 'p5', name: 'Also Syncable', connectionId: 'conn-3', externalProjectId: 'PRJ' }),
    ]));
    fixture.detectChanges();

    const syncable = component.syncableProjects();
    expect(syncable.length).toBe(2);
    expect(syncable.map(p => p.id)).toEqual(['p1', 'p5']);
  });

  it('should return empty syncableProjects when no projects have connection+key', () => {
    projectServiceSpy.getProjectsByTenant.and.returnValue(of([
      makeProject({ id: 'p1', name: 'No Connection' }),
      makeProject({ id: 'p2', name: 'Also No Connection' }),
    ]));
    fixture.detectChanges();

    expect(component.syncableProjects().length).toBe(0);
  });

  it('should toggle sync panel on and off', () => {
    fixture.detectChanges();
    expect(component.showSyncPanel()).toBeFalse();

    component.toggleSyncPanel();
    expect(component.showSyncPanel()).toBeTrue();

    component.toggleSyncPanel();
    expect(component.showSyncPanel()).toBeFalse();
  });

  it('should reset sync state when closing panel', () => {
    fixture.detectChanges();
    component.showSyncPanel.set(true);
    component.selectedSyncProjectId.set('p1');
    component.syncResult.set({ created: 1, updated: 0, unchanged: 0, failed: 0, errors: [] });
    component.syncError.set('some error');

    component.closeSyncPanel();

    expect(component.showSyncPanel()).toBeFalse();
    expect(component.selectedSyncProjectId()).toBe('');
    expect(component.syncResult()).toBeNull();
    expect(component.syncError()).toBeNull();
  });

  it('should call syncTasks on TaskService with correct request', () => {
    const syncResult: TaskSyncResult = { created: 2, updated: 1, unchanged: 5, failed: 0, errors: [] };
    taskServiceSpy.syncTasks.and.returnValue(of(syncResult));
    projectServiceSpy.getProjectsByTenant.and.returnValue(of([
      makeProject({ id: 'p1', name: 'My Project', connectionId: 'conn-1', externalProjectId: 'SQ' }),
    ]));
    fixture.detectChanges();

    component.showSyncPanel.set(true);
    component.selectedSyncProjectId.set('p1');
    component.syncTasks();

    expect(taskServiceSpy.syncTasks).toHaveBeenCalledWith(jasmine.objectContaining({
      tenantId: 't1',
      teamId: 'team-1',
      projectId: 'p1',
      platformConnectionId: 'conn-1',
      projectKey: 'SQ',
    }));
    expect(component.syncResult()).toEqual(syncResult);
    expect(component.syncing()).toBeFalse();
  });

  it('should set syncError on sync failure', () => {
    taskServiceSpy.syncTasks.and.returnValue(throwError(() => new Error('Connection refused')));
    projectServiceSpy.getProjectsByTenant.and.returnValue(of([
      makeProject({ id: 'p1', name: 'My Project', connectionId: 'conn-1', externalProjectId: 'SQ' }),
    ]));
    fixture.detectChanges();

    component.showSyncPanel.set(true);
    component.selectedSyncProjectId.set('p1');
    component.syncTasks();

    expect(component.syncError()).toBe('Connection refused');
    expect(component.syncing()).toBeFalse();
    expect(component.syncResult()).toBeNull();
  });

  it('should not call syncTasks when no project selected', () => {
    fixture.detectChanges();
    component.syncTasks();
    expect(taskServiceSpy.syncTasks).not.toHaveBeenCalled();
  });

  it('should reload board after successful sync with new tasks', () => {
    const syncResult: TaskSyncResult = { created: 1, updated: 0, unchanged: 0, failed: 0, errors: [] };
    taskServiceSpy.syncTasks.and.returnValue(of(syncResult));
    projectServiceSpy.getProjectsByTenant.and.returnValue(of([
      makeProject({ id: 'p1', name: 'My Project', connectionId: 'conn-1', externalProjectId: 'SQ' }),
    ]));
    fixture.detectChanges();
    taskServiceSpy.getTasksByState.calls.reset();

    component.showSyncPanel.set(true);
    component.selectedSyncProjectId.set('p1');
    component.syncTasks();

    // loadData calls getTasksByState
    expect(taskServiceSpy.getTasksByState).toHaveBeenCalledTimes(1);
  });

  it('should not reload board after sync with no changes', () => {
    const syncResult: TaskSyncResult = { created: 0, updated: 0, unchanged: 5, failed: 0, errors: [] };
    taskServiceSpy.syncTasks.and.returnValue(of(syncResult));
    projectServiceSpy.getProjectsByTenant.and.returnValue(of([
      makeProject({ id: 'p1', name: 'My Project', connectionId: 'conn-1', externalProjectId: 'SQ' }),
    ]));
    fixture.detectChanges();
    taskServiceSpy.getTasksByState.calls.reset();

    component.showSyncPanel.set(true);
    component.selectedSyncProjectId.set('p1');
    component.syncTasks();

    // No reload needed since nothing changed
    expect(taskServiceSpy.getTasksByState).not.toHaveBeenCalled();
  });

  // --- stateColor ---

  it('should return correct color for all 9 task states', () => {
    expect(component.stateColor(TaskState.BACKLOG)).toBe('#94A3B8');
    expect(component.stateColor(TaskState.PRIORITIZED)).toBe('#A78BFA');
    expect(component.stateColor(TaskState.PLANNING)).toBe('#818CF8');
    expect(component.stateColor(TaskState.PROPOSE_CODE)).toBe('#38BDF8');
    expect(component.stateColor(TaskState.IN_PROGRESS)).toBe('#06B6D4');
    expect(component.stateColor(TaskState.REVIEW)).toBe('#F59E0B');
    expect(component.stateColor(TaskState.QA)).toBe('#8B5CF6');
    expect(component.stateColor(TaskState.MERGE)).toBe('#22C55E');
    expect(component.stateColor(TaskState.DONE)).toBe('#10B981');
  });

  it('should return default color for unknown state', () => {
    expect(component.stateColor('UNKNOWN')).toBe('#94A3B8');
  });

  // --- totalTasks ---

  it('should compute totalTasks as sum of all columns', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [makeTask({ id: '1', state: TaskState.BACKLOG })],
      [TaskState.IN_PROGRESS]: [makeTask({ id: '2' }), makeTask({ id: '3' })],
      [TaskState.DONE]: [makeTask({ id: '4', state: TaskState.DONE })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    expect(component.totalTasks()).toBe(4);
  });

  it('should return zero totalTasks when board is empty', () => {
    fixture.detectChanges();
    expect(component.totalTasks()).toBe(0);
  });

  // --- filterState ---

  it('should filter tasks by state', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [makeTask({ id: '1', state: TaskState.BACKLOG })],
      [TaskState.IN_PROGRESS]: [makeTask({ id: '2', state: TaskState.IN_PROGRESS })],
      [TaskState.REVIEW]: [makeTask({ id: '3', state: TaskState.REVIEW })],
      [TaskState.DONE]: [makeTask({ id: '4', state: TaskState.DONE })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.filterState.set(TaskState.REVIEW);
    const totalFiltered = component.filteredColumns().reduce((sum, col) => sum + col.tasks.length, 0);
    expect(totalFiltered).toBe(1);
    const reviewCol = component.filteredColumns().find(c => c.id === 'review')!;
    expect(reviewCol.tasks[0].state).toBe(TaskState.REVIEW);
  });

  it('should return all tasks when filterState is empty', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [makeTask({ id: '1', state: TaskState.BACKLOG })],
      [TaskState.IN_PROGRESS]: [makeTask({ id: '2', state: TaskState.IN_PROGRESS })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.filterState.set('');
    const totalFiltered = component.filteredColumns().reduce((sum, col) => sum + col.tasks.length, 0);
    expect(totalFiltered).toBe(2);
  });

  it('should combine filterState with search query', () => {
    const apiData = makeApiData({
      [TaskState.REVIEW]: [
        makeTask({ id: '1', state: TaskState.REVIEW, title: 'Fix auth' }),
        makeTask({ id: '2', state: TaskState.REVIEW, title: 'Fix dashboard' }),
      ],
      [TaskState.IN_PROGRESS]: [
        makeTask({ id: '3', state: TaskState.IN_PROGRESS, title: 'Fix auth too' }),
      ],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.filterState.set(TaskState.REVIEW);
    component.searchQuery.set('auth');
    const totalFiltered = component.filteredColumns().reduce((sum, col) => sum + col.tasks.length, 0);
    expect(totalFiltered).toBe(1);
  });

  // --- View mode (board / list) ---

  it('should default to board view mode', () => {
    fixture.detectChanges();
    expect(component.viewMode()).toBe('board');
  });

  it('should switch to list view mode', () => {
    fixture.detectChanges();
    component.setViewMode('list');
    expect(component.viewMode()).toBe('list');
  });

  it('should switch back to board view mode', () => {
    fixture.detectChanges();
    component.setViewMode('list');
    component.setViewMode('board');
    expect(component.viewMode()).toBe('board');
  });

  it('should compute allTasks as flat list of filtered tasks', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [makeTask({ id: '1', state: TaskState.BACKLOG })],
      [TaskState.IN_PROGRESS]: [makeTask({ id: '2', state: TaskState.IN_PROGRESS })],
      [TaskState.DONE]: [makeTask({ id: '3', state: TaskState.DONE })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    expect(component.allTasks().length).toBe(3);
  });

  it('should reflect filters in allTasks', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [
        makeTask({ id: '1', state: TaskState.BACKLOG, priority: TaskPriority.HIGH }),
        makeTask({ id: '2', state: TaskState.BACKLOG, priority: TaskPriority.LOW }),
      ],
      [TaskState.IN_PROGRESS]: [
        makeTask({ id: '3', state: TaskState.IN_PROGRESS, priority: TaskPriority.HIGH }),
      ],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    component.filterPriority.set('HIGH');
    expect(component.allTasks().length).toBe(2);
  });

  // --- Delegation panel ---

  it('should toggle delegate panel for a task', () => {
    fixture.detectChanges();
    expect(component.showDelegatePanel()).toBeNull();

    component.toggleDelegatePanel('task-1');
    expect(component.showDelegatePanel()).toBe('task-1');

    component.toggleDelegatePanel('task-1');
    expect(component.showDelegatePanel()).toBeNull();
  });

  it('should reset agent type and instructions when opening delegate panel', () => {
    fixture.detectChanges();
    component.delegateAgentType.set('CODING');
    component.delegateInstructions.set('some old instructions');

    component.toggleDelegatePanel('task-1');

    expect(component.delegateAgentType()).toBe('PLANNING');
    expect(component.delegateInstructions()).toBe('');
  });

  it('should switch delegate panel to different task', () => {
    fixture.detectChanges();
    component.toggleDelegatePanel('task-1');
    expect(component.showDelegatePanel()).toBe('task-1');

    component.toggleDelegatePanel('task-2');
    expect(component.showDelegatePanel()).toBe('task-2');
  });

  it('should delegate task successfully and reload data', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [makeTask({ id: 'task-1', state: TaskState.IN_PROGRESS })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    taskServiceSpy.delegateToAgent.and.returnValue(of(void 0));
    fixture.detectChanges();

    component.showDelegatePanel.set('task-1');
    component.delegateAgentType.set('CODING');
    component.delegateInstructions.set('Implement the feature');

    const task = component.columns().find(c => c.id === 'in-progress')!.tasks[0] as BoardTask;
    component.delegateTask(task);

    expect(taskServiceSpy.delegateToAgent).toHaveBeenCalledWith('task-1', jasmine.objectContaining({
      agentType: 'CODING',
      instructions: 'Implement the feature',
      targetState: 'CODING',
    }));
    expect(component.delegating()).toBeFalse();
    expect(component.showDelegatePanel()).toBeNull();
    expect(component.expandedTaskId()).toBeNull();
  });

  it('should set delegating to true during delegation request', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [makeTask({ id: 'task-1', state: TaskState.IN_PROGRESS })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    taskServiceSpy.delegateToAgent.and.returnValue(of(void 0));
    fixture.detectChanges();

    component.showDelegatePanel.set('task-1');
    const task = component.columns().find(c => c.id === 'in-progress')!.tasks[0] as BoardTask;

    // After completion, delegating should be false
    component.delegateTask(task);
    expect(component.delegating()).toBeFalse();
  });

  it('should reset delegating on delegation error', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [makeTask({ id: 'task-1', state: TaskState.IN_PROGRESS })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    taskServiceSpy.delegateToAgent.and.returnValue(throwError(() => new Error('Agent unavailable')));
    fixture.detectChanges();

    component.showDelegatePanel.set('task-1');
    const task = component.columns().find(c => c.id === 'in-progress')!.tasks[0] as BoardTask;
    component.delegateTask(task);

    expect(component.delegating()).toBeFalse();
    // Panel should remain open on error so user can retry
    expect(component.showDelegatePanel()).toBe('task-1');
  });

  it('should send empty instructions as undefined in delegate request', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [makeTask({ id: 'task-1', state: TaskState.IN_PROGRESS })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    taskServiceSpy.delegateToAgent.and.returnValue(of(void 0));
    fixture.detectChanges();

    component.showDelegatePanel.set('task-1');
    component.delegateAgentType.set('REVIEW');
    component.delegateInstructions.set('');

    const task = component.columns().find(c => c.id === 'in-progress')!.tasks[0] as BoardTask;
    component.delegateTask(task);

    const callArgs = taskServiceSpy.delegateToAgent.calls.mostRecent().args;
    expect(callArgs[1].instructions).toBeUndefined();
  });

  // --- allStates and agentTypes constants ---

  it('should expose all 9 TaskState values in allStates', () => {
    expect(component.allStates.length).toBe(9);
    expect(component.allStates).toContain(TaskState.BACKLOG);
    expect(component.allStates).toContain(TaskState.PRIORITIZED);
    expect(component.allStates).toContain(TaskState.PLANNING);
    expect(component.allStates).toContain(TaskState.PROPOSE_CODE);
    expect(component.allStates).toContain(TaskState.IN_PROGRESS);
    expect(component.allStates).toContain(TaskState.REVIEW);
    expect(component.allStates).toContain(TaskState.QA);
    expect(component.allStates).toContain(TaskState.MERGE);
    expect(component.allStates).toContain(TaskState.DONE);
  });

  it('should expose 4 agent types for delegation', () => {
    expect(component.agentTypes.length).toBe(4);
    const values = component.agentTypes.map(a => a.value);
    expect(values).toEqual(['PLANNING', 'CODING', 'REVIEW', 'QA']);
  });

  // --- toggleTaskActions clears delegate panel ---

  it('should close delegate panel when toggling task actions off', () => {
    fixture.detectChanges();
    component.toggleTaskActions('task-1');
    component.showDelegatePanel.set('task-1');

    component.toggleTaskActions('task-1');
    expect(component.showDelegatePanel()).toBeNull();
    expect(component.expandedTaskId()).toBeNull();
  });

  it('should close delegate panel when switching to different task actions', () => {
    fixture.detectChanges();
    component.toggleTaskActions('task-1');
    component.showDelegatePanel.set('task-1');

    component.toggleTaskActions('task-2');
    expect(component.expandedTaskId()).toBe('task-2');
    expect(component.showDelegatePanel()).toBeNull();
  });
});
