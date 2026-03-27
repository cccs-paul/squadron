import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TaskService } from '../../core/services/task.service';
import { TaskState } from '../../core/models/task.model';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';

interface StatCard {
  label: string;
  value: number;
  icon: string;
  color: string;
  bgColor: string;
}

@Component({
  selector: 'sq-dashboard',
  standalone: true,
  imports: [RouterLink, TimeAgoPipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private taskService = inject(TaskService);

  stats = signal<StatCard[]>([
    { label: 'Total Tasks', value: 0, icon: 'total', color: '#4F46E5', bgColor: '#EEF2FF' },
    { label: 'In Progress', value: 0, icon: 'progress', color: '#06B6D4', bgColor: '#ECFEFF' },
    { label: 'Pending Review', value: 0, icon: 'review', color: '#F59E0B', bgColor: '#FEF3C7' },
    { label: 'Completed', value: 0, icon: 'done', color: '#10B981', bgColor: '#D1FAE5' },
  ]);

  stateDistribution = signal<{ state: string; count: number; percentage: number; color: string }[]>([]);
  recentActivity = signal<{ id: string; title: string; action: string; actor: string; time: string }[]>([]);
  loading = signal(true);

  readonly stateColors: Record<string, string> = {
    BACKLOG: '#9CA3AF',
    PLANNING: '#818CF8',
    IN_PROGRESS: '#06B6D4',
    REVIEW: '#F59E0B',
    QA: '#8B5CF6',
    DONE: '#10B981',
  };

  ngOnInit(): void {
    this.loadDashboardData();
  }

  private loadDashboardData(): void {
    this.taskService.getTaskStats().subscribe({
      next: (data) => {
        const byState = data.byState;
        const total = data.total || 0;
        const inProgress = byState?.[TaskState.IN_PROGRESS] || 0;
        const review = byState?.[TaskState.REVIEW] || 0;
        const done = byState?.[TaskState.DONE] || 0;

        this.stats.set([
          { label: 'Total Tasks', value: total, icon: 'total', color: '#4F46E5', bgColor: '#EEF2FF' },
          { label: 'In Progress', value: inProgress, icon: 'progress', color: '#06B6D4', bgColor: '#ECFEFF' },
          { label: 'Pending Review', value: review, icon: 'review', color: '#F59E0B', bgColor: '#FEF3C7' },
          { label: 'Completed', value: done, icon: 'done', color: '#10B981', bgColor: '#D1FAE5' },
        ]);

        const distribution = Object.entries(byState || {}).map(([state, count]) => ({
          state: state.replace('_', ' '),
          count: count as number,
          percentage: total > 0 ? ((count as number) / total) * 100 : 0,
          color: this.stateColors[state] || '#9CA3AF',
        }));
        this.stateDistribution.set(distribution);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        // Use mock data for demo
        this.stats.set([
          { label: 'Total Tasks', value: 47, icon: 'total', color: '#4F46E5', bgColor: '#EEF2FF' },
          { label: 'In Progress', value: 12, icon: 'progress', color: '#06B6D4', bgColor: '#ECFEFF' },
          { label: 'Pending Review', value: 8, icon: 'review', color: '#F59E0B', bgColor: '#FEF3C7' },
          { label: 'Completed', value: 19, icon: 'done', color: '#10B981', bgColor: '#D1FAE5' },
        ]);
        this.stateDistribution.set([
          { state: 'BACKLOG', count: 5, percentage: 10.6, color: '#9CA3AF' },
          { state: 'PLANNING', count: 3, percentage: 6.4, color: '#818CF8' },
          { state: 'IN PROGRESS', count: 12, percentage: 25.5, color: '#06B6D4' },
          { state: 'REVIEW', count: 8, percentage: 17.0, color: '#F59E0B' },
          { state: 'QA', count: 0, percentage: 0, color: '#8B5CF6' },
          { state: 'DONE', count: 19, percentage: 40.4, color: '#10B981' },
        ]);
        this.recentActivity.set([
          { id: '1', title: 'Implement user auth flow', action: 'moved to Review', actor: 'AI Agent', time: new Date(Date.now() - 120000).toISOString() },
          { id: '2', title: 'Fix pagination bug', action: 'completed', actor: 'AI Agent', time: new Date(Date.now() - 300000).toISOString() },
          { id: '3', title: 'Add export CSV feature', action: 'started', actor: 'Jane Smith', time: new Date(Date.now() - 600000).toISOString() },
          { id: '4', title: 'Update API rate limiting', action: 'review approved', actor: 'John Doe', time: new Date(Date.now() - 1800000).toISOString() },
          { id: '5', title: 'Database migration v2', action: 'assigned to AI Agent', actor: 'System', time: new Date(Date.now() - 3600000).toISOString() },
        ]);
      },
    });
  }
}
