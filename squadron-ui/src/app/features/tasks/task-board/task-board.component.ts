import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CdkDragDrop, CdkDrag, CdkDropList, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { TaskService } from '../../../core/services/task.service';
import { Task, TaskState, TaskPriority } from '../../../core/models/task.model';
import { TaskCardComponent } from '../task-card/task-card.component';

interface BoardColumn {
  state: TaskState;
  label: string;
  color: string;
  tasks: Task[];
}

@Component({
  selector: 'sq-task-board',
  standalone: true,
  imports: [FormsModule, TaskCardComponent, CdkDropList, CdkDrag],
  templateUrl: './task-board.component.html',
  styleUrl: './task-board.component.scss',
})
export class TaskBoardComponent implements OnInit {
  private taskService = inject(TaskService);
  private router = inject(Router);

  columns = signal<BoardColumn[]>([]);
  loading = signal(true);
  filterPriority = signal('');
  filterAssignee = signal('');
  searchQuery = signal('');

  filteredColumns = computed(() => {
    const cols = this.columns();
    const search = this.searchQuery().toLowerCase().trim();
    const priority = this.filterPriority();

    if (!search && !priority) {
      return cols;
    }

    return cols.map(col => ({
      ...col,
      tasks: col.tasks.filter(task => {
        const matchesSearch = !search ||
          task.title.toLowerCase().includes(search) ||
          (task.description?.toLowerCase().includes(search)) ||
          (task.externalId?.toLowerCase().includes(search)) ||
          task.labels?.some(l => l.toLowerCase().includes(search));
        const matchesPriority = !priority || task.priority === priority;
        return matchesSearch && matchesPriority;
      }),
    }));
  });

  connectedDropLists = computed(() => this.columns().map(col => `drop-list-${col.state}`));

  ngOnInit(): void {
    this.loadTasks();
  }

  private loadTasks(): void {
    this.loading.set(true);
    const columnDefs: Omit<BoardColumn, 'tasks'>[] = [
      { state: TaskState.BACKLOG, label: 'Backlog', color: '#9CA3AF' },
      { state: TaskState.PLANNING, label: 'Planning', color: '#818CF8' },
      { state: TaskState.IN_PROGRESS, label: 'In Progress', color: '#06B6D4' },
      { state: TaskState.REVIEW, label: 'Review', color: '#F59E0B' },
      { state: TaskState.QA, label: 'QA', color: '#8B5CF6' },
      { state: TaskState.DONE, label: 'Done', color: '#10B981' },
    ];

    this.taskService.getTasksByState().subscribe({
      next: (data) => {
        this.columns.set(
          columnDefs.map((col) => ({
            ...col,
            tasks: data[col.state] || [],
          })),
        );
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load tasks:', err);
        this.columns.set(
          columnDefs.map((col) => ({
            ...col,
            tasks: [],
          })),
        );
        this.loading.set(false);
      },
    });
  }

  openTask(task: Task): void {
    this.router.navigate(['/tasks', task.id]);
  }

  drop(event: CdkDragDrop<Task[]>, targetColumn: BoardColumn): void {
    if (event.previousContainer === event.container) {
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
    } else {
      const task = event.previousContainer.data[event.previousIndex];
      transferArrayItem(
        event.previousContainer.data,
        event.container.data,
        event.previousIndex,
        event.currentIndex,
      );
      // Persist the state transition to the backend
      this.taskService.transitionTask(task.id, targetColumn.state).subscribe({
        error: () => {
          // Revert on failure
          transferArrayItem(
            event.container.data,
            event.previousContainer.data,
            event.currentIndex,
            event.previousIndex,
          );
        },
      });
    }
  }

}
