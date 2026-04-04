import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardComponent } from './dashboard.component';
import { AgentDashboardService } from '../../core/services/agent-dashboard.service';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { AgentDashboard } from '../../core/models/agent.model';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let dashboardServiceSpy: jasmine.SpyObj<AgentDashboardService>;

  const mockDashboard: AgentDashboard = {
    activeAgents: 2,
    idleAgents: 4,
    totalConversations: 10,
    totalTokensUsed: 142300,
    activeWork: [
      {
        conversationId: 'c1',
        taskId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
        agentType: 'CODING',
        status: 'ACTIVE',
        provider: 'openai',
        model: 'gpt-4o',
        totalTokens: 4200,
        startedAt: new Date(Date.now() - 600_000).toISOString(),
        lastActivityAt: new Date().toISOString(),
      },
      {
        conversationId: 'c2',
        taskId: 'ffffffff-1111-2222-3333-444444444444',
        agentType: 'REVIEW',
        status: 'ACTIVE',
        provider: 'openai',
        model: 'gpt-4o',
        totalTokens: 1800,
        startedAt: new Date(Date.now() - 300_000).toISOString(),
        lastActivityAt: new Date().toISOString(),
      },
    ],
    recentActivity: [
      {
        conversationId: 'c1',
        taskId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
        agentType: 'CODING',
        action: 'working',
        totalTokens: 4200,
        timestamp: new Date().toISOString(),
      },
      {
        conversationId: 'c3',
        taskId: 'xxxxxxxx-0000-1111-2222-333333333333',
        agentType: 'QA',
        action: 'completed',
        totalTokens: 3100,
        timestamp: new Date(Date.now() - 1_800_000).toISOString(),
      },
    ],
    agentTypeSummaries: [
      { agentType: 'CODING', activeCount: 1, completedCount: 5, totalTokens: 52400 },
      { agentType: 'REVIEW', activeCount: 1, completedCount: 3, totalTokens: 31000 },
      { agentType: 'QA', activeCount: 0, completedCount: 2, totalTokens: 21000 },
    ],
  };

  beforeEach(async () => {
    dashboardServiceSpy = jasmine.createSpyObj('AgentDashboardService', ['getDashboard']);
    dashboardServiceSpy.getDashboard.and.returnValue(throwError(() => new Error('api down')));

    await TestBed.configureTestingModule({
      imports: [DashboardComponent, TranslateModule.forRoot()],
      providers: [
        { provide: AgentDashboardService, useValue: dashboardServiceSpy },
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should show empty state on API error', () => {
    fixture.detectChanges();
    expect(component.loading()).toBeFalse();
    expect(component.stats().length).toBe(0);
  });

  it('should have empty active work on API error', () => {
    fixture.detectChanges();
    expect(component.activeWork().length).toBe(0);
  });

  it('should have empty recent activity on API error', () => {
    fixture.detectChanges();
    expect(component.recentActivity().length).toBe(0);
  });

  it('should have empty agent type summaries on API error', () => {
    fixture.detectChanges();
    expect(component.agentTypeSummaries().length).toBe(0);
  });

  it('should load real data on successful API response', () => {
    dashboardServiceSpy.getDashboard.and.returnValue(of(mockDashboard));
    fixture.detectChanges();

    expect(component.stats()[0].value).toBe(2);       // activeAgents
    expect(component.stats()[1].value).toBe(4);       // idleAgents
    expect(component.stats()[2].value).toBe(10);      // totalConversations
    expect(component.stats()[3].value).toBe('142.3K'); // totalTokensUsed formatted
    expect(component.loading()).toBeFalse();
  });

  it('should set activeWork from API data', () => {
    dashboardServiceSpy.getDashboard.and.returnValue(of(mockDashboard));
    fixture.detectChanges();

    expect(component.activeWork().length).toBe(2);
    expect(component.activeWork()[0].agentType).toBe('CODING');
    expect(component.activeWork()[1].agentType).toBe('REVIEW');
  });

  it('should set recentActivity from API data', () => {
    dashboardServiceSpy.getDashboard.and.returnValue(of(mockDashboard));
    fixture.detectChanges();

    expect(component.recentActivity().length).toBe(2);
    expect(component.recentActivity()[0].action).toBe('working');
    expect(component.recentActivity()[1].action).toBe('completed');
  });

  it('should set agentTypeSummaries from API data', () => {
    dashboardServiceSpy.getDashboard.and.returnValue(of(mockDashboard));
    fixture.detectChanges();

    expect(component.agentTypeSummaries().length).toBe(3);
    expect(component.agentTypeSummaries()[0].agentType).toBe('CODING');
    expect(component.agentTypeSummaries()[0].totalTokens).toBe(52400);
  });

  it('should render no stat cards on error', () => {
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const statCards = el.querySelectorAll('.stat-card');
    expect(statCards.length).toBe(0);
  });

  it('should render active work cards', () => {
    dashboardServiceSpy.getDashboard.and.returnValue(of(mockDashboard));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const workCards = el.querySelectorAll('.work-card');
    expect(workCards.length).toBe(2);
  });

  it('should show empty state when no active work', () => {
    const emptyDashboard: AgentDashboard = {
      ...mockDashboard,
      activeWork: [],
    };
    dashboardServiceSpy.getDashboard.and.returnValue(of(emptyDashboard));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const emptyState = el.querySelector('.empty-state');
    expect(emptyState).toBeTruthy();
    // Translate pipe returns the key when no translations are loaded
    expect(emptyState!.textContent).toContain('dashboard.allIdle');
  });

  it('should have correct agentTypeColors mapping', () => {
    expect(component.agentTypeColors['CODING']).toBe('#06B6D4');
    expect(component.agentTypeColors['REVIEW']).toBe('#F59E0B');
    expect(component.agentTypeColors['QA']).toBe('#8B5CF6');
    expect(component.agentTypeColors['MERGE']).toBe('#10B981');
    expect(component.agentTypeColors['PLANNING']).toBe('#818CF8');
    expect(component.agentTypeColors['COVERAGE']).toBe('#EC4899');
  });

  it('should format tokens correctly', () => {
    expect(component.formatTokens(500)).toBe('500');
    expect(component.formatTokens(1500)).toBe('1.5K');
    expect(component.formatTokens(1_500_000)).toBe('1.5M');
  });

  it('should return default color for unknown agent type', () => {
    expect(component.getAgentColor('UNKNOWN')).toBe('#9CA3AF');
    expect(component.getAgentBg('UNKNOWN')).toBe('#F3F4F6');
  });

  it('should calculate max tokens from summaries', () => {
    dashboardServiceSpy.getDashboard.and.returnValue(of(mockDashboard));
    fixture.detectChanges();
    expect(component.getMaxTokens()).toBe(52400);
  });

  it('should return 1 for max tokens when no summaries', () => {
    const emptyDashboard: AgentDashboard = {
      ...mockDashboard,
      agentTypeSummaries: [],
    };
    dashboardServiceSpy.getDashboard.and.returnValue(of(emptyDashboard));
    fixture.detectChanges();
    expect(component.getMaxTokens()).toBe(1);
  });

  it('should use labelKey on stats from API data', () => {
    dashboardServiceSpy.getDashboard.and.returnValue(of(mockDashboard));
    fixture.detectChanges();

    expect(component.stats()[0].labelKey).toBe('dashboard.stats.activeAgents');
    expect(component.stats()[1].labelKey).toBe('dashboard.stats.idleAgents');
    expect(component.stats()[2].labelKey).toBe('dashboard.stats.totalConversations');
    expect(component.stats()[3].labelKey).toBe('dashboard.stats.tokensUsed');
  });
});
