import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LowerCasePipe, SlicePipe } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { AgentDashboardService } from '../../core/services/agent-dashboard.service';
import {
  AgentDashboard,
  ActiveAgentWork,
  AgentActivity,
  AgentTypeSummary,
} from '../../core/models/agent.model';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';

interface StatCard {
  labelKey: string;
  value: number | string;
  icon: string;
  color: string;
  bgColor: string;
}

@Component({
  selector: 'sq-dashboard',
  standalone: true,
  imports: [RouterLink, SlicePipe, LowerCasePipe, TimeAgoPipe, TranslateModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private dashboardService = inject(AgentDashboardService);

  stats = signal<StatCard[]>([]);
  activeWork = signal<ActiveAgentWork[]>([]);
  recentActivity = signal<AgentActivity[]>([]);
  agentTypeSummaries = signal<AgentTypeSummary[]>([]);
  loading = signal(true);

  /** Colour mapping for known agent types. */
  readonly agentTypeColors: Record<string, string> = {
    PLANNING: '#818CF8',
    CODING: '#06B6D4',
    REVIEW: '#F59E0B',
    QA: '#8B5CF6',
    MERGE: '#10B981',
    COVERAGE: '#EC4899',
  };

  /** Background-tint for stat icons, keyed by icon name. */
  readonly agentTypeBgColors: Record<string, string> = {
    PLANNING: '#EEF2FF',
    CODING: '#ECFEFF',
    REVIEW: '#FEF3C7',
    QA: '#F3E8FF',
    MERGE: '#D1FAE5',
    COVERAGE: '#FCE7F3',
  };

  ngOnInit(): void {
    this.loadDashboardData();
  }

  getAgentColor(agentType: string): string {
    return this.agentTypeColors[agentType] || '#9CA3AF';
  }

  getAgentBg(agentType: string): string {
    return this.agentTypeBgColors[agentType] || '#F3F4F6';
  }

  formatTokens(tokens: number): string {
    if (tokens >= 1_000_000) return (tokens / 1_000_000).toFixed(1) + 'M';
    if (tokens >= 1_000) return (tokens / 1_000).toFixed(1) + 'K';
    return String(tokens);
  }

  getMaxTokens(): number {
    const summaries = this.agentTypeSummaries();
    if (summaries.length === 0) return 1;
    return Math.max(...summaries.map((s) => s.totalTokens), 1);
  }

  private loadDashboardData(): void {
    this.dashboardService.getDashboard().subscribe({
      next: (data: AgentDashboard) => {
        this.applyData(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load dashboard data:', err);
        this.stats.set([]);
        this.activeWork.set([]);
        this.recentActivity.set([]);
        this.agentTypeSummaries.set([]);
        this.loading.set(false);
      },
    });
  }

  private applyData(data: AgentDashboard): void {
    this.stats.set([
      { labelKey: 'dashboard.stats.activeAgents', value: data.activeAgents, icon: 'active', color: '#06B6D4', bgColor: '#ECFEFF' },
      { labelKey: 'dashboard.stats.idleAgents', value: data.idleAgents, icon: 'idle', color: '#9CA3AF', bgColor: '#F3F4F6' },
      { labelKey: 'dashboard.stats.totalConversations', value: data.totalConversations, icon: 'conversations', color: '#4F46E5', bgColor: '#EEF2FF' },
      { labelKey: 'dashboard.stats.tokensUsed', value: this.formatTokens(data.totalTokensUsed), icon: 'tokens', color: '#F59E0B', bgColor: '#FEF3C7' },
    ]);
    this.activeWork.set(data.activeWork || []);
    this.recentActivity.set(data.recentActivity || []);
    this.agentTypeSummaries.set(data.agentTypeSummaries || []);
  }

}
