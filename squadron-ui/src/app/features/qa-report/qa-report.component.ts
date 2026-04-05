import { Component, inject, OnInit, signal, computed, input } from '@angular/core';
import { QAReportService } from '../../core/services/qa-report.service';
import { QAReport, QAVerdict, QAFinding } from '../../core/models/qa-report.model';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'sq-qa-report',
  standalone: true,
  imports: [RouterLink, TranslateModule],
  templateUrl: './qa-report.component.html',
  styleUrl: './qa-report.component.scss',
})
export class QAReportComponent implements OnInit {
  private qaReportService = inject(QAReportService);
  private route = inject(ActivatedRoute);

  taskId = input<string>('');
  report = signal<QAReport | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  // Computed values for template
  totalTests = computed(() => {
    const r = this.report();
    return r ? r.testsPassed + r.testsFailed + r.testsSkipped : 0;
  });

  passRate = computed(() => {
    const total = this.totalTests();
    const r = this.report();
    return total > 0 && r ? Math.round((r.testsPassed / total) * 100) : 0;
  });

  criticalFindings = computed(() =>
    this.report()?.findings.filter(f => f.severity === 'CRITICAL') ?? []
  );

  majorFindings = computed(() =>
    this.report()?.findings.filter(f => f.severity === 'MAJOR') ?? []
  );

  minorFindings = computed(() =>
    this.report()?.findings.filter(f => f.severity === 'MINOR' || f.severity === 'INFO') ?? []
  );

  ngOnInit(): void {
    const id = this.taskId() || this.route.snapshot.paramMap.get('taskId') || '';
    if (id) {
      this.loadReport(id);
    }
  }

  loadReport(taskId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.qaReportService.getLatestReport(taskId).subscribe({
      next: (report) => {
        this.report.set(report);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load QA report', err);
        this.report.set(null);
        this.error.set('Failed to load QA report');
        this.loading.set(false);
      },
    });
  }

  verdictClass(verdict: QAVerdict): string {
    switch (verdict) {
      case QAVerdict.PASS: return 'success';
      case QAVerdict.CONDITIONAL_PASS: return 'warning';
      case QAVerdict.FAIL: return 'error';
      default: return 'neutral';
    }
  }

  severityClass(severity: string): string {
    switch (severity) {
      case 'CRITICAL': return 'error';
      case 'MAJOR': return 'warning';
      case 'MINOR': return 'primary';
      default: return 'neutral';
    }
  }

  coverageBarColor(): string {
    const pct = this.report()?.coveragePercentage ?? 0;
    if (pct >= 80) return 'var(--sq-success)';
    if (pct >= 60) return 'var(--sq-warning)';
    return 'var(--sq-error)';
  }
}
