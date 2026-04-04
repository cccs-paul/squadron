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
        error: (err) => {
          console.error('Failed to load task:', err);
          this.task.set(null);
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
