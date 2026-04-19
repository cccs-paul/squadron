import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AgentTestService } from './agent-test.service';
import { AgentTestRequest, AgentTestResult, AgentTestConfig } from '../models/agent-test.model';
import { ApiResponse } from '../auth/auth.models';
import { environment } from '../../../environments/environment';

describe('AgentTestService', () => {
  let service: AgentTestService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
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

  it('should_executeTest_when_called', () => {
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

    service.executeTest(request).subscribe((result) => {
      expect(result.testId).toBe('t1');
      expect(result.status).toBe('SUCCESS');
      expect(result.logEntries.length).toBe(1);
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/execute`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(wrapResponse(mockResult));
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

  it('should_unwrapApiResponse_when_executeTestSucceeds', () => {
    const request: AgentTestRequest = { agentConfigId: 'a1', testMode: 'CODE_GENERATION' };
    const mockResult: AgentTestResult = {
      testId: 't2',
      agentConfigId: 'a1',
      testMode: 'CODE_GENERATION',
      status: 'FAILURE',
      summary: 'Test failed',
      logEntries: [],
    };

    service.executeTest(request).subscribe((result) => {
      expect(result.status).toBe('FAILURE');
      expect(result.testMode).toBe('CODE_GENERATION');
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/execute`);
    req.flush(wrapResponse(mockResult));
  });

  it('should_handleHttpError_when_executeTestFails', () => {
    const request: AgentTestRequest = { agentConfigId: 'a1', testMode: 'PLANNING' };

    service.executeTest(request).subscribe({
      error: (err) => {
        expect(err.status).toBe(500);
      },
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/execute`);
    req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });
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

  it('should_executeTest_withCodeReviewMode', () => {
    const request: AgentTestRequest = { agentConfigId: 'a2', testMode: 'CODE_REVIEW' };
    const mockResult: AgentTestResult = {
      testId: 't3',
      agentConfigId: 'a2',
      testMode: 'CODE_REVIEW',
      status: 'SUCCESS',
      summary: 'Review passed',
      logEntries: [],
    };

    service.executeTest(request).subscribe((result) => {
      expect(result.testMode).toBe('CODE_REVIEW');
      expect(result.agentConfigId).toBe('a2');
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/test/execute`);
    expect(req.request.body.testMode).toBe('CODE_REVIEW');
    req.flush(wrapResponse(mockResult));
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
});
