import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { QAReportComponent } from './qa-report.component';
import { QAReportService } from '../../core/services/qa-report.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { QAReport, QAVerdict } from '../../core/models/qa-report.model';

@Component({
  standalone: true,
  imports: [QAReportComponent],
  template: `<sq-qa-report [taskId]="taskId" />`,
})
class TestHostComponent {
  taskId = 'task-123';
}

const mockReport: QAReport = {
  id: 'qa-1',
  tenantId: '1',
  taskId: 'task-123',
  verdict: QAVerdict.CONDITIONAL_PASS,
  summary: 'Tests pass but coverage is below threshold.',
  coveragePercentage: 72.5,
  testsPassed: 145,
  testsFailed: 3,
  testsSkipped: 7,
  findings: [
    { type: 'TEST_FAILURE', message: 'Token expired', filePath: 'src/auth.ts', lineNumber: 123, severity: 'CRITICAL' },
    { type: 'COVERAGE_GAP', message: 'No unit tests', filePath: 'src/auth.ts', lineNumber: 89, severity: 'MAJOR' },
    { type: 'COVERAGE_GAP', message: 'Branch not covered', filePath: 'src/auth.ts', lineNumber: 45, severity: 'MAJOR' },
    { type: 'COVERAGE_GAP', message: 'Missing integration test', filePath: 'src/oauth.ts', severity: 'MINOR' },
    { type: 'TEST_QUALITY', message: 'Hardcoded timeout', filePath: 'src/auth.spec.ts', lineNumber: 67, severity: 'INFO' },
  ],
  gateResult: 'FAILED',
  createdAt: new Date().toISOString(),
};

describe('QAReportComponent', () => {
  let qaServiceSpy: jasmine.SpyObj<QAReportService>;

  function createComponent(useHost: boolean = false): { fixture: ComponentFixture<any>; component: QAReportComponent } {
    if (useHost) {
      const fixture = TestBed.createComponent(TestHostComponent);
      fixture.detectChanges();
      const component = fixture.debugElement.children[0].componentInstance as QAReportComponent;
      return { fixture, component };
    }
    const fixture = TestBed.createComponent(QAReportComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance };
  }

  beforeEach(async () => {
    qaServiceSpy = jasmine.createSpyObj('QAReportService', ['getLatestReport', 'getReports', 'checkGate']);

    await TestBed.configureTestingModule({
      imports: [QAReportComponent, TestHostComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: QAReportService, useValue: qaServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({ taskId: 'task-123' }) },
          },
        },
      ],
    }).compileComponents();
  });

  it('should create', () => {
    qaServiceSpy.getLatestReport.and.returnValue(throwError(() => new Error('fail')));
    const { component } = createComponent();
    expect(component).toBeTruthy();
  });

  it('should load report on init', () => {
    qaServiceSpy.getLatestReport.and.returnValue(of(mockReport));
    const { component } = createComponent(true);
    expect(qaServiceSpy.getLatestReport).toHaveBeenCalledWith('task-123');
    expect(component.report()).toBeTruthy();
    expect(component.report()!.verdict).toBe(QAVerdict.CONDITIONAL_PASS);
    expect(component.loading()).toBeFalse();
  });

  it('should show fallback on service error', () => {
    qaServiceSpy.getLatestReport.and.returnValue(throwError(() => new Error('fail')));
    const { component } = createComponent(true);
    expect(component.report()).toBeTruthy();
    expect(component.report()!.verdict).toBe(QAVerdict.CONDITIONAL_PASS);
    expect(component.loading()).toBeFalse();
  });

  it('should compute totalTests', () => {
    qaServiceSpy.getLatestReport.and.returnValue(of(mockReport));
    const { component } = createComponent(true);
    expect(component.totalTests()).toBe(155);
  });

  it('should compute passRate', () => {
    qaServiceSpy.getLatestReport.and.returnValue(of(mockReport));
    const { component } = createComponent(true);
    expect(component.passRate()).toBe(Math.round((145 / 155) * 100));
  });

  it('should compute critical/major/minor findings', () => {
    qaServiceSpy.getLatestReport.and.returnValue(of(mockReport));
    const { component } = createComponent(true);
    expect(component.criticalFindings().length).toBe(1);
    expect(component.majorFindings().length).toBe(2);
    expect(component.minorFindings().length).toBe(2);
  });

  it('should return correct verdictClass', () => {
    qaServiceSpy.getLatestReport.and.returnValue(throwError(() => new Error('fail')));
    const { component } = createComponent();
    expect(component.verdictClass(QAVerdict.PASS)).toBe('success');
    expect(component.verdictClass(QAVerdict.CONDITIONAL_PASS)).toBe('warning');
    expect(component.verdictClass(QAVerdict.FAIL)).toBe('error');
  });

  it('should return correct severityClass', () => {
    qaServiceSpy.getLatestReport.and.returnValue(throwError(() => new Error('fail')));
    const { component } = createComponent();
    expect(component.severityClass('CRITICAL')).toBe('error');
    expect(component.severityClass('MAJOR')).toBe('warning');
    expect(component.severityClass('MINOR')).toBe('primary');
    expect(component.severityClass('INFO')).toBe('neutral');
  });

  it('should return correct coverageBarColor', () => {
    qaServiceSpy.getLatestReport.and.returnValue(of(mockReport));
    const { component } = createComponent(true);

    // 72.5 is >= 60, so warning
    expect(component.coverageBarColor()).toBe('var(--sq-warning)');

    // Test >= 80
    component.report.set({ ...mockReport, coveragePercentage: 85 });
    expect(component.coverageBarColor()).toBe('var(--sq-success)');

    // Test < 60
    component.report.set({ ...mockReport, coveragePercentage: 50 });
    expect(component.coverageBarColor()).toBe('var(--sq-error)');
  });

  it('should display coverage percentage', () => {
    qaServiceSpy.getLatestReport.and.returnValue(of(mockReport));
    const { fixture } = createComponent(true);
    const el = fixture.nativeElement as HTMLElement;
    const coverageValue = el.querySelector('.coverage-value');
    expect(coverageValue).toBeTruthy();
    expect(coverageValue!.textContent).toContain('72.5%');
  });

  it('should display findings', () => {
    qaServiceSpy.getLatestReport.and.returnValue(of(mockReport));
    const { fixture } = createComponent(true);
    const el = fixture.nativeElement as HTMLElement;
    const findings = el.querySelectorAll('.finding');
    expect(findings.length).toBe(5);
  });
});
