import { Component, input, output } from '@angular/core';
import { Task, TaskPriority } from '../../../core/models/task.model';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { TruncatePipe } from '../../../shared/pipes/truncate.pipe';

@Component({
  selector: 'sq-task-card',
  standalone: true,
  imports: [AvatarComponent, TruncatePipe],
  template: `
    <div class="task-card" (click)="cardClick.emit(task())" [class.task-card--dragging]="false">
      <div class="task-card__header">
        <span class="sq-badge" [class]="'sq-badge--' + priorityClass(task().priority)">
          {{ task().priority }}
        </span>
        @if (task().externalId) {
          <span class="task-card__external-id">{{ task().externalId }}</span>
        }
      </div>
      <h4 class="task-card__title">{{ task().title }}</h4>
      @if (task().description) {
        <p class="task-card__desc">{{ task().description! | truncate:100 }}</p>
      }
      <div class="task-card__footer">
        <div class="task-card__labels">
          @for (label of task().labels.slice(0, 3); track label) {
            <span class="task-card__label">{{ label }}</span>
          }
        </div>
        @if (task().assigneeName) {
          <sq-avatar [name]="task().assigneeName ?? ''" [src]="task().assigneeAvatar" [size]="24" />
        }
      </div>
    </div>
  `,
  styleUrl: './task-card.component.scss',
})
export class TaskCardComponent {
  readonly task = input.required<Task>();
  readonly cardClick = output<Task>();

  priorityClass(priority: TaskPriority): string {
    switch (priority) {
      case TaskPriority.CRITICAL: return 'error';
      case TaskPriority.HIGH: return 'warning';
      case TaskPriority.MEDIUM: return 'primary';
      case TaskPriority.LOW: return 'neutral';
      default: return 'neutral';
    }
  }
}
