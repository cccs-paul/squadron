import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TaskDetailComponent } from './task-detail.component';
import { TaskService } from '../../../core/services/task.service';
import { AgentService, AgentSession } from '../../../core/services/agent.service';
import { UserSquadronService } from '../../../core/services/user-squadron.service';
import { WebSocketService } from '../../../core/services/websocket.service';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError, EMPTY } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { Task, TaskState, TaskPriority } from '../../../core/models/task.model';
import { ConversationSummary } from '../../../core/models/agent.model';
import { UserAgentConfig } from '../../../core/models/squadron-config.model';

describe('TaskDetailComponent', () => {
  let component: TaskDetailComponent;
  let fixture: ComponentFixture<TaskDetailComponent>;
  let taskServiceSpy: jasmine.SpyObj<TaskService>;
  let agentServiceSpy: jasmine.SpyObj<AgentService>;
  let userSquadronServiceSpy: jasmine.SpyObj<UserSquadronService>;
  let wsServiceSpy: jasmine.SpyObj<WebSocketService>;
  let router: Router;

  const mockTask: Task = {
    id: 'task-1',
    tenantId: '1',
    projectId: '1',
    title: 'Implement dashboard',
    description: 'Build the main dashboard',
    state: TaskState.IN_PROGRESS,
    priority: TaskPriority.HIGH,
    labels: ['feature', 'frontend'],
    tokenUsage: 24500,
    assigneeName: 'AI Agent',
    externalId: 'SQ-42',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };

  const mockSummaries: ConversationSummary[] = [
    {
      id: 'conv-1',
      taskId: 'task-1',
      agentType: 'PLANNING',
      status: 'COMPLETED',
      provider: 'github-copilot',
      model: 'claude-sonnet-4',
      totalTokens: 1500,
      messageCount: 8,
      createdAt: '2026-04-01T10:00:00Z',
      updatedAt: '2026-04-01T10:05:00Z',
    },
    {
      id: 'conv-2',
      taskId: 'task-1',
      agentType: 'CODING',
      status: 'ACTIVE',
      provider: 'github-copilot',
      model: 'gpt-4o',
      totalTokens: 5000,
      messageCount: 15,
      createdAt: '2026-04-01T11:00:00Z',
      updatedAt: '2026-04-01T11:30:00Z',
    },
  ];

  const mockSession: AgentSession = {
    id: 'session-1',
    taskId: 'task-1',
    status: 'ACTIVE',
    totalTokens: 5000,
    messages: [],
    createdAt: '2026-04-01T11:00:00Z',
  };

  const mockAgents: UserAgentConfig[] = [
    { agentName: 'Sol', agentType: 'PLANNING', displayOrder: 1, enabled: true, provider: 'ollama', model: 'gemma4' },
    { agentName: 'Titan', agentType: 'CODING', displayOrder: 2, enabled: true, provider: 'ollama', model: 'gemma4' },
    { agentName: 'Disabled', agentType: 'QA', displayOrder: 3, enabled: false },
  ];

  beforeEach(async () => {
    taskServiceSpy = jasmine.createSpyObj('TaskService', ['getTask', 'transitionTask']);
    agentServiceSpy = jasmine.createSpyObj('AgentService', [
      'getConversationSummaries', 'getSession', 'subscribeToProgress',
    ]);
    userSquadronServiceSpy = jasmine.createSpyObj('UserSquadronService', ['getMySquadron']);
    wsServiceSpy = jasmine.createSpyObj('WebSocketService', ['connect', 'subscribe', 'unsubscribe', 'connectionState']);

    // Default mocks
    agentServiceSpy.getConversationSummaries.and.returnValue(of([]));
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('no session')));
    userSquadronServiceSpy.getMySquadron.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [TaskDetailComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TaskService, useValue: taskServiceSpy },
        { provide: AgentService, useValue: agentServiceSpy },
        { provide: UserSquadronService, useValue: userSquadronServiceSpy },
        { provide: WebSocketService, useValue: wsServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({ id: 'task-1' }) },
          },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture = TestBed.createComponent(TaskDetailComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should load task from service on init', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    fixture.detectChanges();
    expect(taskServiceSpy.getTask).toHaveBeenCalledWith('task-1');
    expect(component.task()!.title).toBe('Implement dashboard');
    expect(component.loading()).toBeFalse();
  });

  it('should show empty state on error', () => {
    taskServiceSpy.getTask.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.task()).toBeNull();
    expect(component.loading()).toBeFalse();
  });

  it('should return available transitions for IN_PROGRESS state', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    fixture.detectChanges();
    const transitions = component.getAvailableTransitions();
    expect(transitions).toContain(TaskState.REVIEW);
    expect(transitions).toContain(TaskState.PLANNING);
  });

  it('should return empty transitions for DONE state', () => {
    const doneTask = { ...mockTask, state: TaskState.DONE };
    taskServiceSpy.getTask.and.returnValue(of(doneTask));
    fixture.detectChanges();
    expect(component.getAvailableTransitions()).toEqual([]);
  });

  it('should transition task via service', () => {
    const updatedTask = { ...mockTask, state: TaskState.REVIEW };
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    taskServiceSpy.transitionTask.and.returnValue(of(updatedTask));
    fixture.detectChanges();

    component.transitionTo(TaskState.REVIEW);
    expect(taskServiceSpy.transitionTask).toHaveBeenCalledWith('task-1', TaskState.REVIEW);
    expect(component.task()!.state).toBe(TaskState.REVIEW);
  });

  it('should optimistically update state on transition error', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    taskServiceSpy.transitionTask.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    component.transitionTo(TaskState.REVIEW);
    expect(component.task()!.state).toBe(TaskState.REVIEW);
  });

  it('should return correct priority class', () => {
    expect(component.priorityClass(TaskPriority.CRITICAL)).toBe('error');
    expect(component.priorityClass(TaskPriority.HIGH)).toBe('warning');
    expect(component.priorityClass(TaskPriority.MEDIUM)).toBe('primary');
    expect(component.priorityClass(TaskPriority.LOW)).toBe('neutral');
  });

  it('should return correct state color', () => {
    expect(component.stateColor(TaskState.IN_PROGRESS)).toBe('#06B6D4');
    expect(component.stateColor(TaskState.DONE)).toBe('#10B981');
    expect(component.stateColor(TaskState.BACKLOG)).toBe('#9CA3AF');
  });

  // --- Agent panel tests ---

  it('should load conversation summaries on init', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    agentServiceSpy.getConversationSummaries.and.returnValue(of(mockSummaries));
    fixture.detectChanges();

    expect(agentServiceSpy.getConversationSummaries).toHaveBeenCalledWith('task-1');
    expect(component.conversationSummaries().length).toBe(2);
    expect(component.conversationSummaries()[0].agentType).toBe('PLANNING');
    expect(component.conversationSummaries()[1].agentType).toBe('CODING');
  });

  it('should set empty summaries on error', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    agentServiceSpy.getConversationSummaries.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    expect(component.conversationSummaries()).toEqual([]);
  });

  it('should load agent session on init', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    agentServiceSpy.subscribeToProgress.and.returnValue(EMPTY);
    fixture.detectChanges();

    expect(agentServiceSpy.getSession).toHaveBeenCalledWith('task-1');
    expect(component.agentSession()!.status).toBe('ACTIVE');
  });

  it('should subscribe to progress when session is active', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    agentServiceSpy.subscribeToProgress.and.returnValue(EMPTY);
    fixture.detectChanges();

    expect(wsServiceSpy.connect).toHaveBeenCalled();
    expect(agentServiceSpy.subscribeToProgress).toHaveBeenCalledWith('session-1', wsServiceSpy);
  });

  it('should not subscribe to progress when session is completed', () => {
    const completedSession = { ...mockSession, status: 'COMPLETED' as const };
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    agentServiceSpy.getSession.and.returnValue(of(completedSession));
    fixture.detectChanges();

    expect(wsServiceSpy.connect).not.toHaveBeenCalled();
    expect(agentServiceSpy.subscribeToProgress).not.toHaveBeenCalled();
  });

  it('should load user squadron agents on init', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    userSquadronServiceSpy.getMySquadron.and.returnValue(of(mockAgents));
    fixture.detectChanges();

    expect(userSquadronServiceSpy.getMySquadron).toHaveBeenCalled();
    // Should filter out disabled agents
    expect(component.squadronAgents().length).toBe(2);
    expect(component.squadronAgents().every(a => a.enabled)).toBeTrue();
  });

  it('should compute activeConversation from summaries', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    agentServiceSpy.getConversationSummaries.and.returnValue(of(mockSummaries));
    fixture.detectChanges();

    const active = component.activeConversation();
    expect(active).not.toBeNull();
    expect(active!.status).toBe('ACTIVE');
    expect(active!.agentType).toBe('CODING');
  });

  it('should return null activeConversation when no active summaries', () => {
    const completedOnly = mockSummaries.map(s => ({ ...s, status: 'COMPLETED' }));
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    agentServiceSpy.getConversationSummaries.and.returnValue(of(completedOnly));
    fixture.detectChanges();

    expect(component.activeConversation()).toBeNull();
  });

  it('should navigate to agent chat on openAgentChat', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    fixture.detectChanges();

    component.openAgentChat();
    expect(router.navigate).toHaveBeenCalledWith(['/agent', 'task-1']);
  });

  it('should toggle agent selector', () => {
    expect(component.showAgentSelector()).toBeFalse();
    component.toggleAgentSelector();
    expect(component.showAgentSelector()).toBeTrue();
    component.toggleAgentSelector();
    expect(component.showAgentSelector()).toBeFalse();
  });

  it('should return correct conversation status class', () => {
    expect(component.conversationStatusClass('ACTIVE')).toBe('status--active');
    expect(component.conversationStatusClass('COMPLETED')).toBe('status--completed');
    expect(component.conversationStatusClass('FAILED')).toBe('status--failed');
    expect(component.conversationStatusClass('UNKNOWN')).toBe('status--default');
  });

  it('should compute progressPercent correctly', () => {
    taskServiceSpy.getTask.and.returnValue(of(mockTask));
    fixture.detectChanges();

    // No progress set
    expect(component.progressPercent()).toBe(0);
  });
});
