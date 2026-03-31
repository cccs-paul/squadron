import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AgentService, AgentSession, AgentMessage } from './agent.service';
import { WebSocketService } from './websocket.service';
import { environment } from '../../../environments/environment';
import { Subject } from 'rxjs';
import { StreamChunk, AgentProgress } from '../models/agent.model';

describe('AgentService', () => {
  let service: AgentService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(AgentService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_getSession_when_calledWithTaskId', () => {
    const mockSession: AgentSession = {
      id: 's1',
      taskId: 'task1',
      status: 'ACTIVE',
      totalTokens: 1500,
      messages: [],
      createdAt: '2026-01-01T00:00:00Z',
    };

    service.getSession('task1').subscribe((session) => {
      expect(session).toEqual(mockSession);
    });

    const req = httpTesting.expectOne(`${apiUrl}/agent/sessions/task/task1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSession);
  });

  it('should_sendMessage_when_calledWithSessionIdAndMessage', () => {
    const mockMessage: AgentMessage = {
      id: 'm1',
      sessionId: 's1',
      role: 'USER',
      content: 'Please implement the feature',
      createdAt: '2026-01-01T00:00:00Z',
    };

    service.sendMessage('s1', 'Please implement the feature').subscribe((msg) => {
      expect(msg).toEqual(mockMessage);
    });

    const req = httpTesting.expectOne(`${apiUrl}/agent/sessions/s1/messages`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ content: 'Please implement the feature' });
    req.flush(mockMessage);
  });

  it('should_approvePlan_when_calledWithSessionIdAndPlanId', () => {
    service.approvePlan('s1', 'plan1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/agent/sessions/s1/plans/plan1/approve`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(null);
  });

  it('should_rejectPlan_when_calledWithSessionIdPlanIdAndFeedback', () => {
    service.rejectPlan('s1', 'plan1', 'Needs different approach').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/agent/sessions/s1/plans/plan1/reject`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ feedback: 'Needs different approach' });
    req.flush(null);
  });

  it('should_getMessages_when_calledWithSessionId', () => {
    const mockMessages: AgentMessage[] = [
      { id: 'm1', sessionId: 's1', role: 'USER', content: 'Hello', createdAt: '2026-01-01T00:00:00Z' },
      { id: 'm2', sessionId: 's1', role: 'AGENT', content: 'Hi there!', tokenUsage: 50, createdAt: '2026-01-01T00:00:01Z' },
    ];

    service.getMessages('s1').subscribe((messages) => {
      expect(messages).toEqual(mockMessages);
      expect(messages.length).toBe(2);
    });

    const req = httpTesting.expectOne(`${apiUrl}/agent/sessions/s1/messages`);
    expect(req.request.method).toBe('GET');
    req.flush(mockMessages);
  });

  it('should_returnSessionWithPlan_when_sessionHasCurrentPlan', () => {
    const mockSession: AgentSession = {
      id: 's1',
      taskId: 'task1',
      status: 'WAITING_INPUT',
      totalTokens: 3000,
      messages: [],
      currentPlan: {
        id: 'plan1',
        steps: [
          { id: 'step1', description: 'Analyze code', status: 'COMPLETED', order: 1 },
          { id: 'step2', description: 'Write tests', status: 'IN_PROGRESS', order: 2 },
        ],
        approved: false,
        createdAt: '2026-01-01T00:00:00Z',
      },
      createdAt: '2026-01-01T00:00:00Z',
    };

    service.getSession('task1').subscribe((session) => {
      expect(session.currentPlan).toBeDefined();
      expect(session.currentPlan!.steps.length).toBe(2);
      expect(session.status).toBe('WAITING_INPUT');
    });

    const req = httpTesting.expectOne(`${apiUrl}/agent/sessions/task/task1`);
    req.flush(mockSession);
  });

  it('should_returnEmptyMessages_when_sessionHasNoMessages', () => {
    service.getMessages('s-empty').subscribe((messages) => {
      expect(messages).toEqual([]);
    });

    const req = httpTesting.expectOne(`${apiUrl}/agent/sessions/s-empty/messages`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should_subscribeToStream_when_calledWithConversationId', () => {
    const mockWs = jasmine.createSpyObj('WebSocketService', ['subscribe']);
    const streamSubject = new Subject<StreamChunk>();
    mockWs.subscribe.and.returnValue(streamSubject.asObservable());

    const result = service.subscribeToStream('conv-1', mockWs);
    expect(mockWs.subscribe).toHaveBeenCalledWith('/topic/chat/conv-1');
    expect(result).toBeDefined();

    // Verify the observable emits chunks
    let received: StreamChunk | undefined;
    result.subscribe((chunk) => (received = chunk));
    streamSubject.next({ conversationId: 'conv-1', type: 'chunk', content: 'Hello' });
    expect(received).toEqual({ conversationId: 'conv-1', type: 'chunk', content: 'Hello' });
  });

  it('should_sendStreamingMessage_when_calledWithRequestAndWsService', () => {
    const mockWs = jasmine.createSpyObj('WebSocketService', ['publish']);

    const request = {
      conversationId: 'conv-1',
      taskId: 'task-1',
      agentType: 'CODING',
      message: 'Build a feature',
    };

    service.sendStreamingMessage(request, mockWs);
    expect(mockWs.publish).toHaveBeenCalledWith('/app/chat', request);
  });

  // --- New tests for Feature 2: Progress, Interrupt, and Progress Subscription ---

  it('should_getProgress_when_calledWithSessionId', () => {
    const mockProgress: AgentProgress = {
      conversationId: 'sess-1',
      agentType: 'CODING',
      phase: 'CODING',
      currentStep: 'Writing unit tests',
      completedSteps: 3,
      totalSteps: 5,
      items: [
        { content: 'Analyze requirements', status: 'completed', priority: 'high' },
        { content: 'Create models', status: 'completed', priority: 'high' },
        { content: 'Implement service', status: 'completed', priority: 'high' },
        { content: 'Write unit tests', status: 'in_progress', priority: 'medium' },
        { content: 'Write integration tests', status: 'pending', priority: 'medium' },
      ],
    };

    service.getProgress('sess-1').subscribe((progress) => {
      expect(progress).toEqual(mockProgress);
      expect(progress.completedSteps).toBe(3);
      expect(progress.items.length).toBe(5);
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/sessions/sess-1/progress`);
    expect(req.request.method).toBe('GET');
    req.flush(mockProgress);
  });

  it('should_interruptAgent_when_calledWithSessionIdAndReason', () => {
    service.interruptAgent('sess-1', 'USER_CANCEL').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/agents/sessions/sess-1/interrupt`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ conversationId: 'sess-1', reason: 'USER_CANCEL' });
    req.flush(null);
  });

  it('should_interruptAgent_when_calledWithTimeoutReason', () => {
    service.interruptAgent('sess-2', 'TIMEOUT').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/agents/sessions/sess-2/interrupt`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ conversationId: 'sess-2', reason: 'TIMEOUT' });
    req.flush(null);
  });

  it('should_subscribeToProgress_when_calledWithConversationId', () => {
    const mockWs = jasmine.createSpyObj('WebSocketService', ['subscribe']);
    const progressSubject = new Subject<AgentProgress>();
    mockWs.subscribe.and.returnValue(progressSubject.asObservable());

    const result = service.subscribeToProgress('conv-1', mockWs);
    expect(mockWs.subscribe).toHaveBeenCalledWith('/topic/progress/conv-1');
    expect(result).toBeDefined();

    // Verify the observable emits progress updates
    let received: AgentProgress | undefined;
    result.subscribe((p) => (received = p));
    const mockProgress: AgentProgress = {
      conversationId: 'conv-1',
      agentType: 'PLANNING',
      phase: 'PLANNING',
      currentStep: 'Analyzing task',
      completedSteps: 1,
      totalSteps: 3,
      items: [{ content: 'Analyze task', status: 'completed', priority: 'high' }],
    };
    progressSubject.next(mockProgress);
    expect(received).toEqual(mockProgress);
  });

  it('should_sendInterruptMessage_when_calledViaWebSocket', () => {
    const mockWs = jasmine.createSpyObj('WebSocketService', ['publish']);

    service.sendInterruptMessage({ conversationId: 'conv-1', reason: 'USER_CANCEL' }, mockWs);
    expect(mockWs.publish).toHaveBeenCalledWith('/app/interrupt', {
      conversationId: 'conv-1',
      reason: 'USER_CANCEL',
    });
  });

  it('should_handleInterruptedStreamChunkType', () => {
    const mockWs = jasmine.createSpyObj('WebSocketService', ['subscribe']);
    const streamSubject = new Subject<StreamChunk>();
    mockWs.subscribe.and.returnValue(streamSubject.asObservable());

    const result = service.subscribeToStream('conv-1', mockWs);
    let received: StreamChunk | undefined;
    result.subscribe((chunk) => (received = chunk));

    streamSubject.next({ conversationId: 'conv-1', type: 'interrupted', content: 'Cancelled by user' });
    expect(received).toBeDefined();
    expect(received!.type).toBe('interrupted');
    expect(received!.content).toBe('Cancelled by user');
  });
});
