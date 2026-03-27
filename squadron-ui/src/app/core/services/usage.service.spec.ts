import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { UsageService } from './usage.service';
import { environment } from '../../../environments/environment';

describe('UsageService', () => {
  let service: UsageService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(UsageService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_getTenantSummary_when_calledWithTenantId', () => {
    const mockSummary = {
      totalInputTokens: 50000,
      totalOutputTokens: 30000,
      totalTokens: 80000,
      totalCost: 1.25,
      invocations: 42,
    };

    service.getTenantSummary('t1').subscribe((result) => {
      expect(result).toEqual(mockSummary);
    });

    const req = httpTesting.expectOne(`${apiUrl}/agent/usage/tenant/t1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSummary);
  });

  it('should_getTenantSummary_when_calledWithDateRange', () => {
    service.getTenantSummary('t1', '2026-01-01', '2026-01-31').subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/agent/usage/tenant/t1` &&
      r.params.get('start') === '2026-01-01' &&
      r.params.get('end') === '2026-01-31'
    );
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('should_getUserSummary_when_calledWithTenantIdAndUserId', () => {
    const mockSummary = {
      totalInputTokens: 10000,
      totalOutputTokens: 5000,
      totalTokens: 15000,
      totalCost: 0.50,
      invocations: 10,
    };

    service.getUserSummary('t1', 'u1').subscribe((result) => {
      expect(result).toEqual(mockSummary);
    });

    const req = httpTesting.expectOne(`${apiUrl}/agent/usage/tenant/t1/user/u1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSummary);
  });

  it('should_getTeamSummary_when_calledWithTenantIdAndTeamId', () => {
    service.getTeamSummary('t1', 'team1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/agent/usage/tenant/t1/team/team1`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('should_getByAgentType_when_calledWithTenantId', () => {
    const mockAgents = [
      { agentType: 'PLANNING', totalTokens: 20000, totalCost: 0.30, invocations: 10 },
      { agentType: 'CODING', totalTokens: 40000, totalCost: 0.60, invocations: 15 },
    ];

    service.getByAgentType('t1').subscribe((result) => {
      expect(result).toEqual(mockAgents);
      expect(result.length).toBe(2);
    });

    const req = httpTesting.expectOne(`${apiUrl}/agent/usage/tenant/t1/by-agent`);
    expect(req.request.method).toBe('GET');
    req.flush(mockAgents);
  });
});
