import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { UsageService } from '../../../core/services/usage.service';
import { UsageSummary, UsageByAgent } from '../../../core/models/usage.model';

@Component({
  selector: 'sq-usage-dashboard',
  standalone: true,
  imports: [],
  templateUrl: './usage-dashboard.component.html',
  styleUrl: './usage-dashboard.component.scss',
})
export class UsageDashboardComponent implements OnInit {
  private usageService = inject(UsageService);

  loading = signal(true);
  error = signal<string | null>(null);
  summary = signal<UsageSummary | null>(null);
  agentBreakdown = signal<UsageByAgent[]>([]);

  readonly tenantId = 'demo-tenant-001';

  maxAgentTokens = computed(() => {
    const agents = this.agentBreakdown();
    if (!agents.length) return 0;
    return Math.max(...agents.map((a) => a.totalTokens));
  });

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.error.set(null);

    this.usageService.getTenantSummary(this.tenantId).subscribe({
      next: (data) => {
        this.summary.set(data);
        this.loadAgentBreakdown();
      },
      error: () => {
        this.applyMockData();
        this.loading.set(false);
      },
    });
  }

  formatTokens(value: number): string {
    if (value >= 1_000_000) {
      return (value / 1_000_000).toFixed(1) + 'M';
    }
    if (value >= 1_000) {
      return (value / 1_000).toFixed(1) + 'K';
    }
    return value.toString();
  }

  formatCost(value: number): string {
    return '$' + value.toFixed(2);
  }

  getBarWidth(tokens: number): number {
    const max = this.maxAgentTokens();
    if (!max) return 0;
    return (tokens / max) * 100;
  }

  private loadAgentBreakdown(): void {
    this.usageService.getByAgentType(this.tenantId).subscribe({
      next: (data) => {
        this.agentBreakdown.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.applyMockData();
        this.loading.set(false);
      },
    });
  }

  private applyMockData(): void {
    this.summary.set({
      totalInputTokens: 245000,
      totalOutputTokens: 182000,
      totalTokens: 427000,
      totalCost: 6.84,
      invocations: 156,
    });
    this.agentBreakdown.set([
      { agentType: 'PLANNING', totalTokens: 52000, totalCost: 0.83, invocations: 31 },
      { agentType: 'CODING', totalTokens: 198000, totalCost: 3.17, invocations: 62 },
      { agentType: 'REVIEW', totalTokens: 87000, totalCost: 1.39, invocations: 35 },
      { agentType: 'QA', totalTokens: 65000, totalCost: 1.04, invocations: 20 },
      { agentType: 'MERGE', totalTokens: 25000, totalCost: 0.41, invocations: 8 },
    ]);
  }
}
