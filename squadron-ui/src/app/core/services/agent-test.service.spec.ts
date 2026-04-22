import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AgentTestService } from './agent-test.service';
import { AgentTestRequest, AgentTestResult, AgentTestConfig, InteractiveTestSession } from '../models/agent-test.model';
import { ApiResponse } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';
import { environment } from '../../../environments/environment';

describe('AgentTestService', () => {
  let service: AgentTestService;
  let httpTesting: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getAccessToken']);
    authServiceSpy.getAccessToken.and.returnValue('mock-token');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });
    service = TestBed.inject(AgentTestService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  function wrapResponse<T>(data: T): ApiResponse<T> {
    return { success: true, data, message: '', timestamp: new Date().toISOString() };
  }

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  // executeTest now uses fetch() + SSE, so we test it differently
  it('should_executeTest_withFetch_when_called', (done) => {
    const request: AgentTestRequest = { agentConfigId: 'a1', testMode: 'PLANNING' };
    const mockResult: AgentTestResult = {
      testId: 't1',
      agentConfigId: 'a1',
      testMode: 'PLANNING',
      status: 'SUCCESS',
      summary: 'Test passed',
      logEntries: [{ timestamp: '2026-01-01T00:00:00Z', phase: 'DONE', message: 'ok', level: 'SUCCESS' }],
      durationMs: 1234,
    };

    // Mock fetch to return an SSE stream
    const sseBody = `event:complete\ndata:${JSON.stringify(mockResult)}\n\n`;
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(sseBody));
        controller.close();
      },
    });
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve(new Response(stream, { status: 200, headers: { 'Content-Type': 'text/event-stream' } })),
    );

    const results: AgentTestResult[] = [];
    service.executeTest(request).subscribe({
      next: (result) => results.push(result),
      complete: () => {
        expect(results.length).toBe(1);
        expect(results[0].testId).toBe('t1');
        expect(results[0].status).toBe('SUCCESS');
        expect(globalThis.fetch).toHaveBeenCalledWith(
          `${apiUrl}/agents/test/execute/stream`,
          jasmine.objectContaining({ method: 'POST' }),
        );
        done();
      },
    });
  });

  it('should_emitMultipleProgressEvents_when_streaming', (done) => {
    const request: AgentTestRequest = { agentConfigId: 'a1', testMode: 'CODE_GENERATION' };
    const progress: AgentTestResult = {
      testId: 't1', agentConfigId: 'a1', testMode: 'CODE_GENERATION',
      status: 'RUNNING', logEntries: [{ timestamp: '', phase: 'INIT', message: 'Starting', level: 'INFO' }],
    };
    const complete: AgentTestResult = {
      testId: 't1', agentConfigId: 'a1', testMode: 'CODE_GENERATION',
      status: 'SUCCESS', summary: 'Done', agentOutput: 'output', logEntries: [],
    };

    const sseBody =
      `event:progress\ndata:${JSON.stringify(progress)}\n\n` +
      `event:complete\ndata:${JSON.stringify(complete)}\n\n`;
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(sseBody));
        controller.close();
      },
    });
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve(new Response(stream, { status: 200 })),
    );

    const results: AgentTestResult[] = [];
    service.executeTest(request).subscribe({
      next: (result) => results.push(result),
      complete: () => {
        expect(results.length).toBe(2);
        expect(results[0].status).toBe('RUNNING');
        expect(results[1].status).toBe('SUCCESS');
        expect(results[1].agentOutput).toBe('output');
        done();
      },
    });
  });

  it('should_handleFetchError_when_serverReturns500', (done) => {
    const request: AgentTestRequest = { agentConfigId: 'a1', testMode: 'PLANNING' };

    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve(new Response('Server error', { status: 500, statusText: 'Internal Server Error' })),
    );

    service.executeTest(request).subscribe({
      error: (err) => {
        expect(err.message).toContain('500');
        done();
      },
    });
  });

  it('should_includeAuthToken_when_available', (done) => {
    const request: AgentTestRequest = { agentConfigId: 'a1', testMode: 'PLANNING' };
    const sseBody = `event:complete\ndata:${JSON.stringify({
      testId: 't1', agentConfigId: 'a1', testMode: 'PLANNING',
      status: 'SUCCESS', logEntries: [],
    })}\n\n`;
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(sseBody));
        controller.close();
      },
    });
    const fetchSpy = spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve(new Response(stream, { status: 200 })),
    );

    service.executeTest(request).subscribe({
      complete: () => {
        const [, init] = fetchSpy.calls.mostRecent().args;
        expect((init as any).headers.Authorization).toBe('Bearer mock-token');
        done();
      },
    });
  });

  it('should_abortFetch_when_unsubscribed', () => {
    const request: AgentTestRequest = { agentConfigId: 'a1', testMode: 'PLANNING' };

    // Return a never-resolving stream to keep the subscription alive
    spyOn(globalThis, 'fetch').and.returnValue(
      new Promise(() => {}), // never resolves
    );

    const sub = service.executeTest(request).subscribe();
    // Should not throw when unsubscribing (abort is called)
    expect(() => sub.unsubscribe()).not.toThrow();
  });

  it('should_getTestConfig_when_called', () => {
    const mockConfig: AgentTestConfig = {
      generatorProvider: 'ollama',
      generatorModel: 'gemma4:e2b',
      generatorHostingType: 'SELF_HOSTED',
      generatorBaseUrl: 'http://localhost:11434',
    };

    service.getTestConfig().subscribe((config) => {
      expect(config.generatorProvider).toBe('ollama');
      expect(config.generatorModel).toBe('gemma4:e2b');
      expect(config.generatorHostingType).toBe('SELF_HOSTED');
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/config`);
    expect(req.request.method).toBe('GET');
    req.flush(wrapResponse(mockConfig));
  });

  it('should_updateTestConfig_when_called', () => {
    const config: AgentTestConfig = {
      generatorProvider: 'anthropic',
      generatorModel: 'claude-sonnet-4',
      generatorHostingType: 'PLATFORM',
    };

    service.updateTestConfig(config).subscribe((result) => {
      expect(result.generatorProvider).toBe('anthropic');
      expect(result.generatorModel).toBe('claude-sonnet-4');
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/config`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(config);
    req.flush(wrapResponse(config));
  });

  it('should_handleHttpError_when_getTestConfigFails', () => {
    service.getTestConfig().subscribe({
      error: (err) => {
        expect(err.status).toBe(404);
      },
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/config`);
    req.flush('Not found', { status: 404, statusText: 'Not Found' });
  });

  it('should_handleHttpError_when_updateTestConfigFails', () => {
    const config: AgentTestConfig = {
      generatorProvider: 'openai',
      generatorModel: 'gpt-4o',
      generatorHostingType: 'PLATFORM',
    };

    service.updateTestConfig(config).subscribe({
      error: (err) => {
        expect(err.status).toBe(400);
      },
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/config`);
    req.flush('Bad request', { status: 400, statusText: 'Bad Request' });
  });

  it('should_updateTestConfig_withOptionalFields', () => {
    const config: AgentTestConfig = {
      generatorProvider: 'openai',
      generatorModel: 'gpt-4o',
      generatorHostingType: 'CUSTOM',
      generatorBaseUrl: 'https://custom.api.com/v1',
      generatorApiKey: 'sk-test-key',
    };

    service.updateTestConfig(config).subscribe((result) => {
      expect(result.generatorBaseUrl).toBe('https://custom.api.com/v1');
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/config`);
    expect(req.request.body.generatorBaseUrl).toBe('https://custom.api.com/v1');
    expect(req.request.body.generatorApiKey).toBe('sk-test-key');
    req.flush(wrapResponse(config));
  });

  // --- Interactive test methods ---

  it('should_startInteractiveSession_when_called', (done) => {
    const mockSession: InteractiveTestSession = {
      sessionId: 'sess-1', agentConfigId: 'a1', agentName: 'Sol',
      provider: 'ollama', model: 'gemma4', status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z',
      messages: [{ id: 'm1', role: 'SYSTEM', content: 'Session started', createdAt: '2026-01-01T00:00:00Z' }],
      containerId: 'container-1',
    };

    const sseBody = `data:${JSON.stringify(mockSession)}\n\n`;
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(sseBody));
        controller.close();
      },
    });
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve(new Response(stream, { status: 200, headers: { 'Content-Type': 'text/event-stream' } })),
    );

    service.startInteractiveSession('a1').subscribe({
      next: (session) => {
        expect(session.sessionId).toBe('sess-1');
        expect(session.agentName).toBe('Sol');
        expect(session.status).toBe('ACTIVE');
      },
      complete: () => {
        expect(globalThis.fetch).toHaveBeenCalledWith(
          `${apiUrl}/agents/test/interactive/start?agentConfigId=a1`,
          jasmine.objectContaining({ method: 'POST' }),
        );
        done();
      },
    });
  });

  it('should_getInteractiveSession_when_called', () => {
    const mockSession: InteractiveTestSession = {
      sessionId: 'sess-1', agentConfigId: 'a1', agentName: 'Sol',
      provider: 'ollama', model: 'gemma4', status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z', messages: [], containerId: 'container-1',
    };

    service.getInteractiveSession('sess-1').subscribe((session) => {
      expect(session.sessionId).toBe('sess-1');
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/interactive/sess-1`);
    expect(req.request.method).toBe('GET');
    req.flush(wrapResponse(mockSession));
  });

  it('should_getInteractiveSessions_when_called', () => {
    service.getInteractiveSessions().subscribe((sessions) => {
      expect(sessions.length).toBe(1);
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/interactive/sessions`);
    expect(req.request.method).toBe('GET');
    req.flush(wrapResponse([{
      sessionId: 'sess-1', agentConfigId: 'a1', agentName: 'Sol',
      provider: 'ollama', model: 'gemma4', status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z', messages: [], containerId: 'container-1',
    }]));
  });

  it('should_closeInteractiveSession_when_called', () => {
    service.closeInteractiveSession('sess-1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/interactive/sess-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should_sendInteractiveMessage_withFetch_when_called', (done) => {
    const mockSnapshot: InteractiveTestSession = {
      sessionId: 'sess-1', agentConfigId: 'a1', agentName: 'Sol',
      provider: 'ollama', model: 'gemma4', status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z', containerId: 'container-1',
      messages: [
        { id: 'm1', role: 'SYSTEM', content: 'Session started', createdAt: '2026-01-01T00:00:00Z' },
        { id: 'm2', role: 'USER', content: 'Hello', createdAt: '2026-01-01T00:00:01Z' },
        { id: 'm3', role: 'AGENT', content: 'Hi there!', createdAt: '2026-01-01T00:00:02Z' },
      ],
    };

    const sseBody = `event:snapshot\ndata:${JSON.stringify(mockSnapshot)}\n\n`;
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(sseBody));
        controller.close();
      },
    });
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve(new Response(stream, { status: 200 })),
    );

    const snapshots: InteractiveTestSession[] = [];
    service.sendInteractiveMessage('sess-1', 'Hello').subscribe({
      next: (snapshot) => snapshots.push(snapshot),
      complete: () => {
        expect(snapshots.length).toBe(1);
        expect(snapshots[0].messages.length).toBe(3);
        expect(snapshots[0].messages[2].role).toBe('AGENT');
        expect(globalThis.fetch).toHaveBeenCalledWith(
          `${apiUrl}/agents/test/interactive/message/stream`,
          jasmine.objectContaining({ method: 'POST' }),
        );
        done();
      },
    });
  });

  it('should_handleFetchError_when_sendInteractiveMessageFails', (done) => {
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve(new Response('Server error', { status: 500, statusText: 'Internal Server Error' })),
    );

    service.sendInteractiveMessage('sess-1', 'Hi').subscribe({
      error: (err) => {
        expect(err.message).toContain('500');
        done();
      },
    });
  });

  it('should_abortFetch_when_sendInteractiveMessageUnsubscribed', () => {
    spyOn(globalThis, 'fetch').and.returnValue(
      new Promise(() => {}), // never resolves
    );

    const sub = service.sendInteractiveMessage('sess-1', 'Hi').subscribe();
    expect(() => sub.unsubscribe()).not.toThrow();
  });

  it('should_emitMultipleSnapshots_when_sendInteractiveMessageStreams', (done) => {
    const snapshot1: InteractiveTestSession = {
      sessionId: 'sess-1', agentConfigId: 'a1', agentName: 'Sol',
      provider: 'ollama', model: 'gemma4', status: 'STREAMING',
      createdAt: '2026-01-01T00:00:00Z', containerId: 'container-1',
      messages: [
        { id: 'm1', role: 'USER', content: 'Hi', createdAt: '2026-01-01T00:00:00Z' },
        { id: 'm2', role: 'AGENT', content: 'Hel', createdAt: '2026-01-01T00:00:01Z' },
      ],
    };
    const snapshot2: InteractiveTestSession = {
      sessionId: 'sess-1', agentConfigId: 'a1', agentName: 'Sol',
      provider: 'ollama', model: 'gemma4', status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z', containerId: 'container-1',
      messages: [
        { id: 'm1', role: 'USER', content: 'Hi', createdAt: '2026-01-01T00:00:00Z' },
        { id: 'm2', role: 'AGENT', content: 'Hello there!', createdAt: '2026-01-01T00:00:01Z' },
      ],
    };

    const sseBody =
      `event:snapshot\ndata:${JSON.stringify(snapshot1)}\n\n` +
      `event:snapshot\ndata:${JSON.stringify(snapshot2)}\n\n`;
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(sseBody));
        controller.close();
      },
    });
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve(new Response(stream, { status: 200 })),
    );

    const snapshots: InteractiveTestSession[] = [];
    service.sendInteractiveMessage('sess-1', 'Hi').subscribe({
      next: (snapshot) => snapshots.push(snapshot),
      complete: () => {
        expect(snapshots.length).toBe(2);
        expect(snapshots[0].status).toBe('STREAMING');
        expect(snapshots[1].status).toBe('ACTIVE');
        done();
      },
    });
  });

  it('should_handleHttpError_when_startInteractiveSessionFails', (done) => {
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve(new Response(null, { status: 500, statusText: 'Internal Server Error' })),
    );

    service.startInteractiveSession('a1').subscribe({
      error: (err) => {
        expect(err.message).toContain('500');
        done();
      },
    });
  });

  it('should_handleHttpError_when_closeInteractiveSessionFails', () => {
    service.closeInteractiveSession('sess-1').subscribe({
      error: (err) => {
        expect(err.status).toBe(404);
      },
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/interactive/sess-1`);
    req.flush('Not found', { status: 404, statusText: 'Not Found' });
  });

  it('should_handleHttpError_when_getInteractiveSessionFails', () => {
    service.getInteractiveSession('sess-1').subscribe({
      error: (err) => {
        expect(err.status).toBe(500);
      },
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/interactive/sess-1`);
    req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });
  });

  it('should_handleHttpError_when_getInteractiveSessionsFails', () => {
    service.getInteractiveSessions().subscribe({
      error: (err) => {
        expect(err.status).toBe(500);
      },
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/interactive/sessions`);
    req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });
  });
});
