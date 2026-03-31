import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AgentDashboardService } from './agent-dashboard.service';
import { AgentDashboard } from '../models/agent.model';

describe('AgentDashboardService', () => {
  let service: AgentDashboardService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AgentDashboardService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AgentDashboardService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should call GET /agents/dashboard and unwrap ApiResponse', () => {
    const mockDashboard: AgentDashboard = {
      activeAgents: 2,
      idleAgents: 4,
      totalConversations: 10,
      totalTokensUsed: 5000,
      activeWork: [],
      recentActivity: [],
      agentTypeSummaries: [],
    };

    service.getDashboard().subscribe((result) => {
      expect(result).toEqual(mockDashboard);
      expect(result.activeAgents).toBe(2);
      expect(result.idleAgents).toBe(4);
    });

    const req = httpTesting.expectOne((r) => r.url.includes('/agents/dashboard'));
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, data: mockDashboard, message: null, timestamp: new Date().toISOString() });
  });
});
