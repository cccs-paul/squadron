import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService, PageResponse } from './api.service';
import { Task, TaskFilter, TaskState, TaskSyncRequest, TaskSyncResult } from '../models/task.model';
import { ApiResponse } from '../auth/auth.models';

@Injectable({ providedIn: 'root' })
export class TaskService extends ApiService {
  getTasks(filter?: TaskFilter, page = 0, size = 50): Observable<PageResponse<Task>> {
    const params: Record<string, string | number> = { page, size };
    if (filter?.state) params['state'] = filter.state;
    if (filter?.priority) params['priority'] = filter.priority;
    if (filter?.assigneeId) params['assigneeId'] = filter.assigneeId;
    if (filter?.projectId) params['projectId'] = filter.projectId;
    if (filter?.search) params['search'] = filter.search;
    return this.get<PageResponse<Task>>('/tasks', params);
  }

  getTask(id: string): Observable<Task> {
    return this.get<Task>(`/tasks/${id}`);
  }

  createTask(task: Partial<Task>): Observable<Task> {
    return this.post<Task>('/tasks', task);
  }

  updateTask(id: string, task: Partial<Task>): Observable<Task> {
    return this.put<Task>(`/tasks/${id}`, task);
  }

  transitionTask(id: string, toState: TaskState): Observable<Task> {
    return this.post<Task>(`/tasks/${id}/transition`, { toState });
  }

  getTasksByState(): Observable<Record<TaskState, Task[]>> {
    return this.get<Record<TaskState, Task[]>>('/tasks/by-state');
  }

  getTaskStats(): Observable<{ total: number; byState: Record<TaskState, number>; byPriority: Record<string, number> }> {
    return this.get('/tasks/stats');
  }

  syncTasks(request: TaskSyncRequest): Observable<TaskSyncResult> {
    return this.post<ApiResponse<TaskSyncResult>>('/tasks/sync', request).pipe(
      map((response) => response.data),
    );
  }
}
