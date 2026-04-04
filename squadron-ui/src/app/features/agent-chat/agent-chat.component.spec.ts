import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AgentChatComponent } from './agent-chat.component';
import { AgentService, AgentSession, AgentMessage } from '../../core/services/agent.service';
import { WebSocketService, ConnectionState } from '../../core/services/websocket.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError, Subject } from 'rxjs';
import { StreamChunk, AgentProgress } from '../../core/models/agent.model';

describe('AgentChatComponent', () => {
  let component: AgentChatComponent;
  let fixture: ComponentFixture<AgentChatComponent>;
  let agentServiceSpy: jasmine.SpyObj<AgentService>;
  let wsServiceSpy: jasmine.SpyObj<WebSocketService>;
  let streamSubject: Subject<StreamChunk>;
  let progressSubject: Subject<AgentProgress>;

  beforeEach(async () => {
    agentServiceSpy = jasmine.createSpyObj('AgentService', [
      'getSession', 'sendMessage', 'subscribeToStream', 'sendStreamingMessage',
      'getProgress', 'interruptAgent', 'subscribeToProgress', 'sendInterruptMessage',
    ]);
    wsServiceSpy = jasmine.createSpyObj('WebSocketService', [
      'connect', 'disconnect', 'subscribe', 'unsubscribe', 'publish', 'connectionState',
    ]);
    // Default: WebSocket is disconnected
    wsServiceSpy.connectionState.and.returnValue('disconnected' as ConnectionState);

    streamSubject = new Subject<StreamChunk>();
    progressSubject = new Subject<AgentProgress>();
    agentServiceSpy.subscribeToStream.and.returnValue(streamSubject.asObservable());
    agentServiceSpy.subscribeToProgress.and.returnValue(progressSubject.asObservable());

    await TestBed.configureTestingModule({
      imports: [AgentChatComponent],
      providers: [
        { provide: AgentService, useValue: agentServiceSpy },
        { provide: WebSocketService, useValue: wsServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({ taskId: 'task-1' }) },
          },
        },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AgentChatComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should_showEmptyState_when_sessionErrors', () => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.messages().length).toBe(0);
    expect(component.loading()).toBeFalse();
  });

  it('should_loadSession_when_serviceReturnsSession', () => {
    const mockSession: AgentSession = {
      id: 's1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 5000,
      messages: [
        { id: 'm1', sessionId: 's1', role: 'SYSTEM', content: 'started', createdAt: new Date().toISOString() },
      ],
      createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    fixture.detectChanges();
    expect(component.session()!.id).toBe('s1');
    expect(component.messages().length).toBe(1);
  });

  it('should_connectWebSocket_when_sessionLoaded', () => {
    const mockSession: AgentSession = {
      id: 's1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    fixture.detectChanges();
    expect(wsServiceSpy.connect).toHaveBeenCalled();
    expect(agentServiceSpy.subscribeToStream).toHaveBeenCalledWith('s1', wsServiceSpy);
  });

  it('should_notSendEmptyMessages', () => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.newMessage = '   ';
    component.sendMessage();
    expect(component.messages().length).toBe(0);
  });

  it('should_addUserMessage_when_sending', () => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    const initialCount = component.messages().length;
    component.newMessage = 'Hello agent';
    component.sendMessage();
    expect(component.messages().length).toBe(initialCount + 1);
    const lastMsg = component.messages()[component.messages().length - 1];
    expect(lastMsg.role).toBe('USER');
    expect(lastMsg.content).toBe('Hello agent');
  });

  it('should_clearInput_when_messageSent', () => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    component.newMessage = 'Hello';
    component.sendMessage();
    expect(component.newMessage).toBe('');
  });

  it('should_stopSending_when_noSessionAndNoWebSocket', fakeAsync(() => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    const initialCount = component.messages().length;
    component.newMessage = 'Help me';
    component.sendMessage();
    tick(1100);
    // With no session, only the user message is added and sending stops immediately
    expect(component.messages().length).toBe(initialCount + 1);
    expect(component.sending()).toBeFalse();
  }));

  it('should_formatCodeBlocks_correctly', () => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    const result = component.formatCodeBlocks('hello `world` **bold**');
    expect(result).toContain('<code class="inline-code">world</code>');
    expect(result).toContain('<strong>bold</strong>');
  });

  it('should_sendViaWebSocket_when_connected', () => {
    const mockSession: AgentSession = {
      id: 'conv-1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    wsServiceSpy.connectionState.and.returnValue('connected' as ConnectionState);
    fixture.detectChanges();

    component.newMessage = 'Build a feature';
    component.sendMessage();

    expect(agentServiceSpy.sendStreamingMessage).toHaveBeenCalledWith(
      jasmine.objectContaining({
        conversationId: 'conv-1',
        taskId: 'task-1',
        agentType: 'CODING',
        message: 'Build a feature',
      }),
      wsServiceSpy,
    );
    expect(component.sending()).toBeTrue();
  });

  it('should_accumulateStreamingContent_when_chunksArrive', () => {
    const mockSession: AgentSession = {
      id: 'conv-1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    fixture.detectChanges();

    streamSubject.next({ conversationId: 'conv-1', type: 'chunk', content: 'Hello ' });
    expect(component.streamingContent()).toBe('Hello ');

    streamSubject.next({ conversationId: 'conv-1', type: 'chunk', content: 'world!' });
    expect(component.streamingContent()).toBe('Hello world!');
  });

  it('should_finalizeMessage_when_doneChunkArrives', () => {
    const mockSession: AgentSession = {
      id: 'conv-1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    fixture.detectChanges();

    // Simulate streaming
    streamSubject.next({ conversationId: 'conv-1', type: 'chunk', content: 'Full response' });
    expect(component.streamingContent()).toBe('Full response');

    // Done signal
    streamSubject.next({ conversationId: 'conv-1', type: 'done', messageId: 'msg-1', tokenCount: 250 });

    expect(component.streamingContent()).toBe('');
    expect(component.sending()).toBeFalse();
    const lastMsg = component.messages()[component.messages().length - 1];
    expect(lastMsg.role).toBe('AGENT');
    expect(lastMsg.content).toBe('Full response');
    expect(lastMsg.tokenUsage).toBe(250);
  });

  it('should_addErrorMessage_when_errorChunkArrives', () => {
    const mockSession: AgentSession = {
      id: 'conv-1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    fixture.detectChanges();

    streamSubject.next({ conversationId: 'conv-1', type: 'error', content: 'Rate limited' });

    expect(component.sending()).toBeFalse();
    expect(component.streamingContent()).toBe('');
    const lastMsg = component.messages()[component.messages().length - 1];
    expect(lastMsg.role).toBe('SYSTEM');
    expect(lastMsg.content).toContain('Rate limited');
  });

  it('should_fallbackToHttp_when_webSocketNotConnected', () => {
    const mockSession: AgentSession = {
      id: 'conv-1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    // WebSocket is NOT connected
    wsServiceSpy.connectionState.and.returnValue('disconnected' as ConnectionState);
    fixture.detectChanges();

    const mockResponse: AgentMessage = {
      id: 'r1', sessionId: 'conv-1', role: 'AGENT',
      content: 'HTTP response', createdAt: new Date().toISOString(), tokenUsage: 100,
    };
    agentServiceSpy.sendMessage.and.returnValue(of(mockResponse));

    component.newMessage = 'Help me';
    component.sendMessage();

    expect(agentServiceSpy.sendMessage).toHaveBeenCalledWith('conv-1', 'Help me');
  });

  it('should_unsubscribeAndCleanup_when_destroyed', () => {
    const mockSession: AgentSession = {
      id: 'conv-1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    fixture.detectChanges();

    component.ngOnDestroy();
    expect(wsServiceSpy.unsubscribe).toHaveBeenCalledWith('/topic/chat/conv-1');
    expect(wsServiceSpy.unsubscribe).toHaveBeenCalledWith('/topic/progress/conv-1');
  });

  it('should_haveInitialStreamingContentEmpty', () => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.streamingContent()).toBe('');
  });

  it('should_haveInitialConnectionStateDisconnected', () => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.connectionState()).toBe('disconnected');
  });

  // --- New tests for Feature 2: Progress, Cancel, Interrupted ---

  it('should_subscribeToProgress_when_sessionLoaded', () => {
    const mockSession: AgentSession = {
      id: 'conv-1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    fixture.detectChanges();

    expect(agentServiceSpy.subscribeToProgress).toHaveBeenCalledWith('conv-1', wsServiceSpy);
  });

  it('should_updateProgress_when_progressEventArrives', () => {
    const mockSession: AgentSession = {
      id: 'conv-1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    fixture.detectChanges();

    const mockProgress: AgentProgress = {
      conversationId: 'conv-1',
      agentType: 'CODING',
      phase: 'CODING',
      currentStep: 'Writing tests',
      completedSteps: 2,
      totalSteps: 4,
      items: [
        { content: 'Analyze code', status: 'completed', priority: 'high' },
        { content: 'Create models', status: 'completed', priority: 'high' },
        { content: 'Write tests', status: 'in_progress', priority: 'medium' },
        { content: 'Deploy', status: 'pending', priority: 'low' },
      ],
    };
    progressSubject.next(mockProgress);

    expect(component.progress()).toEqual(mockProgress);
    expect(component.progressPercent).toBe(0.5);
  });

  it('should_setNullProgress_when_sessionErrors', () => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    expect(component.progress()).toBeNull();
  });

  it('should_cancelAgentViaWebSocket_when_connected', () => {
    const mockSession: AgentSession = {
      id: 'conv-1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    wsServiceSpy.connectionState.and.returnValue('connected' as ConnectionState);
    fixture.detectChanges();

    component.cancelAgent();
    expect(agentServiceSpy.sendInterruptMessage).toHaveBeenCalledWith(
      { conversationId: 'conv-1', reason: 'USER_CANCEL' },
      wsServiceSpy,
    );
  });

  it('should_cancelAgentViaRest_when_wsDisconnected', () => {
    const mockSession: AgentSession = {
      id: 'conv-1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    wsServiceSpy.connectionState.and.returnValue('disconnected' as ConnectionState);
    agentServiceSpy.interruptAgent.and.returnValue(of(undefined));
    fixture.detectChanges();

    component.cancelAgent();
    expect(agentServiceSpy.interruptAgent).toHaveBeenCalledWith('conv-1', 'USER_CANCEL');
  });

  it('should_handleInterruptedChunk_when_agentInterrupted', () => {
    const mockSession: AgentSession = {
      id: 'conv-1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    fixture.detectChanges();

    // Simulate partial streaming then interrupt
    streamSubject.next({ conversationId: 'conv-1', type: 'chunk', content: 'Partial output' });
    expect(component.streamingContent()).toBe('Partial output');

    streamSubject.next({ conversationId: 'conv-1', type: 'interrupted', content: 'Agent was interrupted by user.' });

    expect(component.streamingContent()).toBe('');
    expect(component.sending()).toBeFalse();
    expect(component.interrupted()).toBeTrue();

    // Partial content should be saved as AGENT message
    const msgs = component.messages();
    const agentMsg = msgs[msgs.length - 2]; // second-to-last (partial content)
    expect(agentMsg.role).toBe('AGENT');
    expect(agentMsg.content).toBe('Partial output');

    // System message about interruption
    const sysMsg = msgs[msgs.length - 1];
    expect(sysMsg.role).toBe('SYSTEM');
    expect(sysMsg.content).toContain('interrupted');
  });

  it('should_handleInterruptedChunk_withoutPartialContent', () => {
    const mockSession: AgentSession = {
      id: 'conv-1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    fixture.detectChanges();

    const initialCount = component.messages().length;
    streamSubject.next({ conversationId: 'conv-1', type: 'interrupted', content: 'Agent was interrupted by user.' });

    expect(component.interrupted()).toBeTrue();
    // Only system message added (no partial AGENT message)
    expect(component.messages().length).toBe(initialCount + 1);
    expect(component.messages()[component.messages().length - 1].role).toBe('SYSTEM');
  });

  it('should_resetInterrupted_when_newMessageSent', () => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    component.interrupted.set(true);
    component.newMessage = 'Continue working';
    component.sendMessage();

    expect(component.interrupted()).toBeFalse();
  });

  it('should_calculateProgressPercent_correctly', () => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    // With null progress (from error), percent should be 0
    expect(component.progressPercent).toBe(0);

    // With set progress (2/4 steps)
    component.progress.set({
      conversationId: 'x', agentType: 'CODING', phase: 'CODING',
      currentStep: 'Writing tests', completedSteps: 2, totalSteps: 4, items: [],
    });
    expect(component.progressPercent).toBe(0.5);

    // With zero total steps
    component.progress.set({
      conversationId: 'x', agentType: 'CODING', phase: 'CODING',
      currentStep: 'Starting', completedSteps: 0, totalSteps: 0, items: [],
    });
    expect(component.progressPercent).toBe(0);

    // With null progress
    component.progress.set(null);
    expect(component.progressPercent).toBe(0);
  });

  it('should_unsubscribeFromProgress_when_destroyed', () => {
    const mockSession: AgentSession = {
      id: 'conv-1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    fixture.detectChanges();

    component.ngOnDestroy();
    expect(wsServiceSpy.unsubscribe).toHaveBeenCalledWith('/topic/progress/conv-1');
  });

  it('should_haveInitialProgressNull_when_sessionLoadsSuccessfully', () => {
    const mockSession: AgentSession = {
      id: 's1', taskId: 'task-1', status: 'ACTIVE', totalTokens: 0,
      messages: [], createdAt: new Date().toISOString(),
    };
    agentServiceSpy.getSession.and.returnValue(of(mockSession));
    fixture.detectChanges();

    // Progress is null until a progress event arrives via WebSocket
    expect(component.progress()).toBeNull();
  });

  it('should_haveInitialInterruptedFalse', () => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.interrupted()).toBeFalse();
  });
});
