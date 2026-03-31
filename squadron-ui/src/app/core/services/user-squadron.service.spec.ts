import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { UserSquadronService } from './user-squadron.service';
import { UserAgentConfig, SquadronLimits } from '../models/squadron-config.model';
import { ApiResponse } from '../auth/auth.models';
import { environment } from '../../../environments/environment';

describe('UserSquadronService', () => {
  let service: UserSquadronService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(UserSquadronService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  function wrapResponse<T>(data: T): ApiResponse<T> {
    return { success: true, data, message: '', timestamp: new Date().toISOString() };
  }

  function mockAgent(overrides: Partial<UserAgentConfig> = {}): UserAgentConfig {
    return {
      id: 'agent-1',
      agentName: 'Maverick',
      agentType: 'CODING',
      displayOrder: 0,
      enabled: true,
      ...overrides,
    };
  }

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_getMySquadron_when_called', () => {
    const mockAgents: UserAgentConfig[] = [
      mockAgent({ id: 'a1', agentName: 'Architect', agentType: 'PLANNING', displayOrder: 0 }),
      mockAgent({ id: 'a2', agentName: 'Maverick', agentType: 'CODING', displayOrder: 1 }),
    ];

    service.getMySquadron().subscribe((agents) => {
      expect(agents.length).toBe(2);
      expect(agents[0].agentName).toBe('Architect');
      expect(agents[1].agentType).toBe('CODING');
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/squadron`);
    expect(req.request.method).toBe('GET');
    req.flush(wrapResponse(mockAgents));
  });

  it('should_getLimits_when_called', () => {
    const limits: SquadronLimits = { maxAgentsPerUser: 12 };

    service.getLimits().subscribe((result) => {
      expect(result.maxAgentsPerUser).toBe(12);
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/squadron/limits`);
    expect(req.request.method).toBe('GET');
    req.flush(wrapResponse(limits));
  });

  it('should_addAgent_when_calledWithData', () => {
    const newAgent: Partial<UserAgentConfig> = {
      agentName: 'New Agent',
      agentType: 'REVIEW',
      displayOrder: 3,
      enabled: true,
    };
    const savedAgent = mockAgent({ id: 'new-1', agentName: 'New Agent', agentType: 'REVIEW' });

    service.addAgent(newAgent).subscribe((agent) => {
      expect(agent.id).toBe('new-1');
      expect(agent.agentName).toBe('New Agent');
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/squadron`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(newAgent);
    req.flush(wrapResponse(savedAgent));
  });

  it('should_updateAgent_when_calledWithIdAndData', () => {
    const update: Partial<UserAgentConfig> = {
      agentName: 'Updated',
      agentType: 'QA',
    };
    const updated = mockAgent({ id: 'a1', agentName: 'Updated', agentType: 'QA' });

    service.updateAgent('a1', update).subscribe((agent) => {
      expect(agent.agentName).toBe('Updated');
      expect(agent.agentType).toBe('QA');
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/squadron/a1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(update);
    req.flush(wrapResponse(updated));
  });

  it('should_removeAgent_when_calledWithId', () => {
    service.removeAgent('a1').subscribe((result) => {
      expect(result).toBeUndefined();
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/squadron/a1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(wrapResponse(null));
  });

  it('should_resetToDefaults_when_called', () => {
    const defaults: UserAgentConfig[] = [
      mockAgent({ id: 'd1', agentName: 'Architect', agentType: 'PLANNING' }),
      mockAgent({ id: 'd2', agentName: 'Maverick', agentType: 'CODING' }),
    ];

    service.resetToDefaults().subscribe((agents) => {
      expect(agents.length).toBe(2);
      expect(agents[0].agentName).toBe('Architect');
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/squadron/reset`);
    expect(req.request.method).toBe('POST');
    req.flush(wrapResponse(defaults));
  });

  it('should_unwrapApiResponse_when_getMySquadronSucceeds', () => {
    service.getMySquadron().subscribe((agents) => {
      expect(agents).toEqual([]);
    });

    const req = httpTesting.expectOne(`${apiUrl}/agents/squadron`);
    req.flush(wrapResponse([]));
  });

  it('should_sendEmptyBodyForReset_when_callingResetToDefaults', () => {
    service.resetToDefaults().subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/agents/squadron/reset`);
    expect(req.request.body).toEqual({});
    req.flush(wrapResponse([]));
  });
});
