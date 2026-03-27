import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { TaskService } from '../../../core/services/task.service';
import { Task, TaskState, TaskPriority } from '../../../core/models/task.model';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';

@Component({
  selector: 'sq-task-detail',
  standalone: true,
  imports: [RouterLink, AvatarComponent, TimeAgoPipe, DecimalPipe],
  templateUrl: './task-detail.component.html',
  styleUrl: './task-detail.component.scss',
})
export class TaskDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private taskService = inject(TaskService);

  task = signal<Task | null>(null);
  loading = signal(true);

  readonly stateTransitions: Record<string, TaskState[]> = {
    BACKLOG: [TaskState.PLANNING],
    PLANNING: [TaskState.IN_PROGRESS, TaskState.BACKLOG],
    IN_PROGRESS: [TaskState.REVIEW, TaskState.PLANNING],
    REVIEW: [TaskState.QA, TaskState.IN_PROGRESS],
    QA: [TaskState.DONE, TaskState.IN_PROGRESS],
    DONE: [],
  };

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.taskService.getTask(id).subscribe({
        next: (task) => { this.task.set(task); this.loading.set(false); },
        error: () => {
          this.task.set({
            id: id, tenantId: '1', projectId: '1', title: 'Implement user dashboard', description: 'Create a comprehensive dashboard view that shows task statistics, recent activity, and quick action buttons. The dashboard should include charts for task distribution and a real-time activity feed.',
            state: TaskState.IN_PROGRESS, priority: TaskPriority.HIGH, labels: ['feature', 'frontend'], tokenUsage: 24500,
            assigneeName: 'AI Agent', externalId: 'SQ-42', externalUrl: 'https://github.com/org/repo/issues/42',
            pullRequestUrl: 'https://github.com/org/repo/pull/43',
            createdAt: new Date(Date.now() - 86400000).toISOString(), updatedAt: new Date(Date.now() - 3600000).toISOString(),
          } as Task);
          this.loading.set(false);
        },
      });
    }
  }

  transitionTo(state: TaskState): void {
    const current = this.task();
    if (!current) return;
    this.taskService.transitionTask(current.id, state).subscribe({
      next: (updated) => this.task.set(updated),
      error: () => this.task.update((t) => t ? { ...t, state } : t),
    });
  }

  getAvailableTransitions(): TaskState[] {
    const t = this.task();
    if (!t) return [];
    return this.stateTransitions[t.state] || [];
  }

  priorityClass(priority: TaskPriority): string {
    switch (priority) {
      case TaskPriority.CRITICAL: return 'error';
      case TaskPriority.HIGH: return 'warning';
      case TaskPriority.MEDIUM: return 'primary';
      default: return 'neutral';
    }
  }

  stateColor(state: TaskState): string {
    const colors: Record<string, string> = {
      BACKLOG: '#9CA3AF', PLANNING: '#818CF8', IN_PROGRESS: '#06B6D4',
      REVIEW: '#F59E0B', QA: '#8B5CF6', DONE: '#10B981',
    };
    return colors[state] || '#9CA3AF';
  }
}
