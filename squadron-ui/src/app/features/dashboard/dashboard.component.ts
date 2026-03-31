import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { AgentDashboardService } from '../../core/services/agent-dashboard.service';
import {
  AgentDashboard,
  ActiveAgentWork,
  AgentActivity,
  AgentTypeSummary,
} from '../../core/models/agent.model';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';

interface StatCard {
  label: string;
  value: number | string;
  icon: string;
  color: string;
  bgColor: string;
}

@Component({
  selector: 'sq-dashboard',
  standalone: true,
  imports: [RouterLink, SlicePipe, TimeAgoPipe],
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
      error: () => {
        this.applyMockData();
        this.loading.set(false);
      },
    });
  }

  private applyData(data: AgentDashboard): void {
    this.stats.set([
      { label: 'Active Agents', value: data.activeAgents, icon: 'active', color: '#06B6D4', bgColor: '#ECFEFF' },
      { label: 'Idle Agents', value: data.idleAgents, icon: 'idle', color: '#9CA3AF', bgColor: '#F3F4F6' },
      { label: 'Total Conversations', value: data.totalConversations, icon: 'conversations', color: '#4F46E5', bgColor: '#EEF2FF' },
      { label: 'Tokens Used', value: this.formatTokens(data.totalTokensUsed), icon: 'tokens', color: '#F59E0B', bgColor: '#FEF3C7' },
    ]);
    this.activeWork.set(data.activeWork || []);
    this.recentActivity.set(data.recentActivity || []);
    this.agentTypeSummaries.set(data.agentTypeSummaries || []);
  }

  private applyMockData(): void {
    const now = new Date().toISOString();
    const fiveMinAgo = new Date(Date.now() - 300_000).toISOString();
    const tenMinAgo = new Date(Date.now() - 600_000).toISOString();
    const thirtyMinAgo = new Date(Date.now() - 1_800_000).toISOString();
    const oneHrAgo = new Date(Date.now() - 3_600_000).toISOString();

    this.stats.set([
      { label: 'Active Agents', value: 3, icon: 'active', color: '#06B6D4', bgColor: '#ECFEFF' },
      { label: 'Idle Agents', value: 3, icon: 'idle', color: '#9CA3AF', bgColor: '#F3F4F6' },
      { label: 'Total Conversations', value: 24, icon: 'conversations', color: '#4F46E5', bgColor: '#EEF2FF' },
      { label: 'Tokens Used', value: '142.3K', icon: 'tokens', color: '#F59E0B', bgColor: '#FEF3C7' },
    ]);

    this.activeWork.set([
      { conversationId: 'c1', taskId: 't1', agentType: 'CODING', status: 'ACTIVE', provider: 'openai', model: 'gpt-4o', totalTokens: 4200, startedAt: tenMinAgo, lastActivityAt: now },
      { conversationId: 'c2', taskId: 't2', agentType: 'REVIEW', status: 'ACTIVE', provider: 'openai', model: 'gpt-4o', totalTokens: 1800, startedAt: fiveMinAgo, lastActivityAt: now },
      { conversationId: 'c3', taskId: 't3', agentType: 'PLANNING', status: 'ACTIVE', provider: 'anthropic', model: 'claude-3.5', totalTokens: 900, startedAt: thirtyMinAgo, lastActivityAt: fiveMinAgo },
    ]);

    this.recentActivity.set([
      { conversationId: 'c1', taskId: 't1', agentType: 'CODING', action: 'working', totalTokens: 4200, timestamp: now },
      { conversationId: 'c2', taskId: 't2', agentType: 'REVIEW', action: 'working', totalTokens: 1800, timestamp: fiveMinAgo },
      { conversationId: 'c4', taskId: 't4', agentType: 'QA', action: 'completed', totalTokens: 3100, timestamp: thirtyMinAgo },
      { conversationId: 'c5', taskId: 't5', agentType: 'MERGE', action: 'completed', totalTokens: 600, timestamp: oneHrAgo },
      { conversationId: 'c3', taskId: 't3', agentType: 'PLANNING', action: 'working', totalTokens: 900, timestamp: fiveMinAgo },
    ]);

    this.agentTypeSummaries.set([
      { agentType: 'PLANNING', activeCount: 1, completedCount: 5, totalTokens: 18200 },
      { agentType: 'CODING', activeCount: 1, completedCount: 8, totalTokens: 52400 },
      { agentType: 'REVIEW', activeCount: 1, completedCount: 4, totalTokens: 31000 },
      { agentType: 'QA', activeCount: 0, completedCount: 3, totalTokens: 21400 },
      { agentType: 'MERGE', activeCount: 0, completedCount: 3, totalTokens: 12600 },
      { agentType: 'COVERAGE', activeCount: 0, completedCount: 1, totalTokens: 6700 },
    ]);
  }
}
