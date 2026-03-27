import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AgentChatComponent } from './agent-chat.component';
import { AgentService, AgentSession, AgentMessage } from '../../core/services/agent.service';
import { WebSocketService, ConnectionState } from '../../core/services/websocket.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError, Subject } from 'rxjs';
import { StreamChunk } from '../../core/models/agent.model';

describe('AgentChatComponent', () => {
  let component: AgentChatComponent;
  let fixture: ComponentFixture<AgentChatComponent>;
  let agentServiceSpy: jasmine.SpyObj<AgentService>;
  let wsServiceSpy: jasmine.SpyObj<WebSocketService>;
  let streamSubject: Subject<StreamChunk>;

  beforeEach(async () => {
    agentServiceSpy = jasmine.createSpyObj('AgentService', [
      'getSession', 'sendMessage', 'subscribeToStream', 'sendStreamingMessage',
    ]);
    wsServiceSpy = jasmine.createSpyObj('WebSocketService', [
      'connect', 'disconnect', 'subscribe', 'unsubscribe', 'publish', 'connectionState',
    ]);
    // Default: WebSocket is disconnected
    wsServiceSpy.connectionState.and.returnValue('disconnected' as ConnectionState);

    streamSubject = new Subject<StreamChunk>();
    agentServiceSpy.subscribeToStream.and.returnValue(streamSubject.asObservable());

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

  it('should_loadMockMessages_when_sessionErrors', () => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component.messages().length).toBe(4);
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
    expect(component.messages().length).toBe(4);
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

  it('should_addMockResponse_when_noSessionAndNoWebSocket', fakeAsync(() => {
    agentServiceSpy.getSession.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    const initialCount = component.messages().length;
    component.newMessage = 'Help me';
    component.sendMessage();
    tick(1100);
    expect(component.messages().length).toBe(initialCount + 2);
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
    const initialCount = component.messages().length;

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
});
