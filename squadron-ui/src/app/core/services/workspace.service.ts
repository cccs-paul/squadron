import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

export interface Workspace {
  id: string;
  tenantId: string;
  taskId: string;
  status: 'PROVISIONING' | 'READY' | 'RUNNING' | 'STOPPING' | 'STOPPED' | 'FAILED';
  containerId?: string;
  createdAt: string;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class WorkspaceService extends ApiService {
  getWorkspaceByTask(taskId: string): Observable<Workspace> {
    return this.get<Workspace>(`/workspaces/task/${taskId}`);
  }

  destroyWorkspace(workspaceId: string): Observable<void> {
    return this.delete<void>(`/workspaces/${workspaceId}`);
  }
}
