import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from './api.service';
import { ApiResponse } from '../auth/auth.models';

export interface Workspace {
  id: string;
  tenantId: string;
  taskId: string;
  status: 'PROVISIONING' | 'READY' | 'RUNNING' | 'STOPPING' | 'STOPPED' | 'FAILED';
  containerId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface TestGitAccessRequest {
  cloneUrl: string;
  accessToken?: string;
  sshKeyId?: string;
  branch?: string;
}

export interface TestGitAccessResult {
  success: boolean;
  message: string;
  branch?: string;
  durationMs: number;
}

@Injectable({ providedIn: 'root' })
export class WorkspaceService extends ApiService {
  getWorkspaceByTask(taskId: string): Observable<Workspace> {
    return this.get<Workspace>(`/workspaces/task/${taskId}`);
  }

  destroyWorkspace(workspaceId: string): Observable<void> {
    return this.delete<void>(`/workspaces/${workspaceId}`);
  }

  testGitAccess(request: TestGitAccessRequest): Observable<TestGitAccessResult> {
    return this.post<ApiResponse<TestGitAccessResult>>('/workspaces/test-git-access', request).pipe(
      map((response) => response.data),
    );
  }
}
