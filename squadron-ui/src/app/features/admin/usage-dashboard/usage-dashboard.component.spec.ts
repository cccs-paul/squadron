import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UsageDashboardComponent } from './usage-dashboard.component';
import { UsageService } from '../../../core/services/usage.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { UsageSummary, UsageByAgent } from '../../../core/models/usage.model';

describe('UsageDashboardComponent', () => {
  let component: UsageDashboardComponent;
  let fixture: ComponentFixture<UsageDashboardComponent>;
  let usageServiceSpy: jasmine.SpyObj<UsageService>;

  const mockSummary: UsageSummary = {
    totalInputTokens: 245000,
    totalOutputTokens: 182000,
    totalTokens: 427000,
    totalCost: 6.84,
    invocations: 156,
  };

  const mockAgents: UsageByAgent[] = [
    { agentType: 'PLANNING', totalTokens: 52000, totalCost: 0.83, invocations: 31 },
    { agentType: 'CODING', totalTokens: 198000, totalCost: 3.17, invocations: 62 },
    { agentType: 'REVIEW', totalTokens: 87000, totalCost: 1.39, invocations: 35 },
    { agentType: 'QA', totalTokens: 65000, totalCost: 1.04, invocations: 20 },
    { agentType: 'MERGE', totalTokens: 25000, totalCost: 0.41, invocations: 8 },
  ];

  beforeEach(async () => {
    usageServiceSpy = jasmine.createSpyObj('UsageService', [
      'getTenantSummary',
      'getByAgentType',
    ]);

    await TestBed.configureTestingModule({
      imports: [UsageDashboardComponent],
      providers: [
        { provide: UsageService, useValue: usageServiceSpy },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UsageDashboardComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    usageServiceSpy.getTenantSummary.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should show loading state initially', () => {
    usageServiceSpy.getTenantSummary.and.returnValue(throwError(() => new Error('fail')));
    expect(component.loading()).toBeTrue();
  });

  it('should display summary data after load', () => {
    usageServiceSpy.getTenantSummary.and.returnValue(of(mockSummary));
    usageServiceSpy.getByAgentType.and.returnValue(of(mockAgents));
    fixture.detectChanges();

    expect(component.summary()).toEqual(mockSummary);
    expect(component.loading()).toBeFalse();
  });

  it('should display agent breakdown', () => {
    usageServiceSpy.getTenantSummary.and.returnValue(of(mockSummary));
    usageServiceSpy.getByAgentType.and.returnValue(of(mockAgents));
    fixture.detectChanges();

    expect(component.agentBreakdown().length).toBe(5);
    expect(component.agentBreakdown()[0].agentType).toBe('PLANNING');
  });

  it('should show empty state when no data', () => {
    usageServiceSpy.getTenantSummary.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    // Error handler sets summary to null and agentBreakdown to empty
    expect(component.summary()).toBeNull();
    expect(component.agentBreakdown().length).toBe(0);

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.usage-dashboard__empty')).toBeTruthy();
  });

  it('should format cost to 2 decimal places', () => {
    expect(component.formatCost(6.84)).toBe('$6.84');
    expect(component.formatCost(0.1)).toBe('$0.10');
    expect(component.formatCost(100)).toBe('$100.00');
  });

  it('should format large token numbers', () => {
    expect(component.formatTokens(427000)).toBe('427.0K');
    expect(component.formatTokens(1500000)).toBe('1.5M');
    expect(component.formatTokens(500)).toBe('500');
  });

  it('should call service on init', () => {
    usageServiceSpy.getTenantSummary.and.returnValue(of(mockSummary));
    usageServiceSpy.getByAgentType.and.returnValue(of(mockAgents));
    fixture.detectChanges();

    expect(usageServiceSpy.getTenantSummary).toHaveBeenCalledWith('demo-tenant-001');
    expect(usageServiceSpy.getByAgentType).toHaveBeenCalledWith('demo-tenant-001');
  });

  it('should calculate correct bar widths', () => {
    usageServiceSpy.getTenantSummary.and.returnValue(of(mockSummary));
    usageServiceSpy.getByAgentType.and.returnValue(of(mockAgents));
    fixture.detectChanges();

    // CODING has max (198000), so its bar should be 100%
    expect(component.getBarWidth(198000)).toBe(100);
    // PLANNING (52000) should be ~26.3%
    expect(component.getBarWidth(52000)).toBeCloseTo(26.3, 0);
  });

  it('should show empty state on API error', () => {
    usageServiceSpy.getTenantSummary.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    expect(component.summary()).toBeNull();
    expect(component.agentBreakdown().length).toBe(0);
    expect(component.loading()).toBeFalse();
  });
});
