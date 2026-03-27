import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { DiffResult, CodeGenerationStatus } from '../models/diff.model';

@Injectable({ providedIn: 'root' })
export class DiffService extends ApiService {

  getTaskDiff(taskId: string): Observable<DiffResult> {
    return this.get<DiffResult>(`/api/git/pull-requests/task/${taskId}/diff`);
  }

  getCodeGenerationStatus(taskId: string): Observable<CodeGenerationStatus> {
    return this.get<CodeGenerationStatus>(`/api/agents/coding/status/${taskId}`);
  }

  getPullRequestDiff(prId: string): Observable<DiffResult> {
    return this.get<DiffResult>(`/api/git/pull-requests/${prId}/diff`);
  }
}
