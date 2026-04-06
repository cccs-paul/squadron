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
import { TaskState, TaskPriority, Task } from '../../../core/models/task.model';
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
    [TaskState.PLANNING]: [],
    [TaskState.IN_PROGRESS]: [],
    [TaskState.REVIEW]: [],
    [TaskState.QA]: [],
    [TaskState.DONE]: [],
    ...overrides,
  };
}

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
    taskServiceSpy = jasmine.createSpyObj('TaskService', ['getTasksByState', 'transitionTask']);
    projectServiceSpy = jasmine.createSpyObj('ProjectService', ['getProjectsByTenant']);
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

  it('should create 3 columns on successful load', () => {
    fixture.detectChanges();
    expect(component.columns().length).toBe(3);
    const ids = component.columns().map(c => c.id);
    expect(ids).toEqual(['in-progress', 'planned', 'completed']);
  });

  it('should create 3 empty columns on API error', () => {
    taskServiceSpy.getTasksByState.and.returnValue(throwError(() => new Error('api down')));
    fixture.detectChanges();
    expect(component.columns().length).toBe(3);
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

  it('should group IN_PROGRESS, REVIEW, QA into in-progress column', () => {
    const apiData = makeApiData({
      [TaskState.IN_PROGRESS]: [makeTask({ id: '1', state: TaskState.IN_PROGRESS })],
      [TaskState.REVIEW]: [makeTask({ id: '2', state: TaskState.REVIEW })],
      [TaskState.QA]: [makeTask({ id: '3', state: TaskState.QA })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    const inProgress = component.columns().find(c => c.id === 'in-progress');
    expect(inProgress!.tasks.length).toBe(3);
  });

  it('should group BACKLOG and PLANNING into planned column', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [makeTask({ id: '1', state: TaskState.BACKLOG })],
      [TaskState.PLANNING]: [makeTask({ id: '2', state: TaskState.PLANNING })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    const planned = component.columns().find(c => c.id === 'planned');
    expect(planned!.tasks.length).toBe(2);
  });

  it('should put DONE tasks into completed column', () => {
    const apiData = makeApiData({
      [TaskState.DONE]: [makeTask({ id: '1', state: TaskState.DONE })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    const completed = component.columns().find(c => c.id === 'completed');
    expect(completed!.tasks.length).toBe(1);
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
      [TaskState.IN_PROGRESS]: [makeTask({ id: '1' }), makeTask({ id: '2' })],
      [TaskState.REVIEW]: [makeTask({ id: '3' })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    expect(component.totalInProgress()).toBe(3);
  });

  it('should compute totalPlanned correctly', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [makeTask({ id: '1' })],
      [TaskState.PLANNING]: [makeTask({ id: '2' })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    expect(component.totalPlanned()).toBe(2);
  });

  it('should compute totalCompleted correctly', () => {
    const apiData = makeApiData({
      [TaskState.DONE]: [makeTask({ id: '1' }), makeTask({ id: '2' }), makeTask({ id: '3' })],
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

  it('should return all columns from filteredColumns when no filters set', () => {
    fixture.detectChanges();
    const filtered = component.filteredColumns();
    expect(filtered.length).toBe(3);
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
    const planned = component.filteredColumns().find(c => c.id === 'planned')!;
    expect(planned.tasks.length).toBe(1);
    expect(planned.tasks[0].priority).toBe(TaskPriority.HIGH);
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
    const planned = component.filteredColumns().find(c => c.id === 'planned')!;
    expect(planned.tasks.length).toBe(1);
    expect(planned.tasks[0].title).toContain('export');
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

  // --- Connected drop lists ---

  it('should compute connectedDropLists with column IDs', () => {
    fixture.detectChanges();
    const lists = component.connectedDropLists();
    expect(lists.length).toBe(3);
    expect(lists).toEqual(['drop-list-in-progress', 'drop-list-planned', 'drop-list-completed']);
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

  it('should move cancelled task from in-progress to planned column', () => {
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
    expect(component.columns().find(c => c.id === 'planned')!.tasks.length).toBe(1);
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

    const plannedCol = component.columns().find(c => c.id === 'planned')!;
    const containerData = [...plannedCol.tasks];
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

    component.drop(event as CdkDragDrop<BoardTask[]>, plannedCol);
    expect(containerData[0].title).toBe('Second');
    expect(containerData[1].title).toBe('First');
  });

  it('should call transitionTask when task dropped to different column', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [makeTask({ id: '1', state: TaskState.BACKLOG, title: 'Task A' })],
      [TaskState.IN_PROGRESS]: [makeTask({ id: '2', state: TaskState.IN_PROGRESS, title: 'Task B' })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    const plannedCol = component.columns().find(c => c.id === 'planned')!;
    const inProgressCol = component.columns().find(c => c.id === 'in-progress')!;
    const sourceData = [...plannedCol.tasks];
    const targetData = [...inProgressCol.tasks];
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

    component.drop(event as CdkDragDrop<BoardTask[]>, inProgressCol);
    // Target state is first state of the target column = IN_PROGRESS
    expect(taskServiceSpy.transitionTask).toHaveBeenCalledWith('1', TaskState.IN_PROGRESS);
    expect(sourceData.length).toBe(0);
    expect(targetData.length).toBe(2);
  });

  it('should revert transfer on failed transition', () => {
    const apiData = makeApiData({
      [TaskState.BACKLOG]: [makeTask({ id: '1', state: TaskState.BACKLOG })],
      [TaskState.IN_PROGRESS]: [makeTask({ id: '2', state: TaskState.IN_PROGRESS })],
    });
    taskServiceSpy.getTasksByState.and.returnValue(of(apiData));
    fixture.detectChanges();

    const plannedCol = component.columns().find(c => c.id === 'planned')!;
    const inProgressCol = component.columns().find(c => c.id === 'in-progress')!;
    const sourceData = [...plannedCol.tasks];
    const targetData = [...inProgressCol.tasks];
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

    component.drop(event as CdkDragDrop<BoardTask[]>, inProgressCol);
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
});
