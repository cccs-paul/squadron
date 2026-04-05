import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { UsageService } from '../../../core/services/usage.service';
import { UsageSummary, UsageByAgent } from '../../../core/models/usage.model';

@Component({
  selector: 'sq-usage-dashboard',
  standalone: true,
  imports: [TranslateModule],
  templateUrl: './usage-dashboard.component.html',
  styleUrl: './usage-dashboard.component.scss',
})
export class UsageDashboardComponent implements OnInit {
  private usageService = inject(UsageService);

  loading = signal(true);
  error = signal<string | null>(null);
  summary = signal<UsageSummary | null>(null);
  agentBreakdown = signal<UsageByAgent[]>([]);

  tenantId = '';

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
        console.error('Failed to load tenant usage summary');
        this.summary.set(null);
        this.agentBreakdown.set([]);
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
        console.error('Failed to load agent breakdown');
        this.agentBreakdown.set([]);
        this.loading.set(false);
      },
    });
  }
}
