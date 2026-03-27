import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AgentConfigService, AgentConfig } from './agent-config.service';
import { environment } from '../../../environments/environment';

describe('AgentConfigService', () => {
  let service: AgentConfigService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(AgentConfigService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_getConfig_when_calledWithTenantId', () => {
    const mockConfig: AgentConfig = {
      id: 'cfg1',
      tenantId: 't1',
      provider: 'OpenAI',
      modelName: 'gpt-4',
      temperature: 0.7,
      maxTokens: 4096,
      systemPrompt: 'You are a coding assistant.',
    };

    service.getConfig('t1').subscribe((result) => {
      expect(result).toEqual(mockConfig);
    });

    const req = httpTesting.expectOne(`${apiUrl}/agent/config/t1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockConfig);
  });

  it('should_updateConfig_when_calledWithTenantIdAndConfig', () => {
    const update: Partial<AgentConfig> = {
      temperature: 0.5,
      maxTokens: 8192,
    };

    const mockResponse: AgentConfig = {
      id: 'cfg1',
      tenantId: 't1',
      provider: 'OpenAI',
      modelName: 'gpt-4',
      temperature: 0.5,
      maxTokens: 8192,
    };

    service.updateConfig('t1', update).subscribe((result) => {
      expect(result.temperature).toBe(0.5);
      expect(result.maxTokens).toBe(8192);
    });

    const req = httpTesting.expectOne(`${apiUrl}/agent/config/t1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(update);
    req.flush(mockResponse);
  });

  it('should_getConfig_when_calledWithDifferentTenantId', () => {
    service.getConfig('tenant-xyz').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/agent/config/tenant-xyz`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});
