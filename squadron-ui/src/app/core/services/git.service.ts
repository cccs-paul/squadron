import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

export interface GitRepository {
  id: string;
  name: string;
  fullName: string;
  url: string;
  defaultBranch: string;
  platform: string;
}

export interface GitBranch {
  name: string;
  sha: string;
  isDefault: boolean;
}

@Injectable({ providedIn: 'root' })
export class GitService extends ApiService {
  getRepositories(connectionId: string): Observable<GitRepository[]> {
    return this.get<GitRepository[]>(`/git/connections/${connectionId}/repositories`);
  }

  getBranches(connectionId: string, repoName: string): Observable<GitBranch[]> {
    return this.get<GitBranch[]>(`/git/connections/${connectionId}/repositories/${encodeURIComponent(repoName)}/branches`);
  }

  getPullRequestStatus(taskId: string): Observable<{ url: string; state: string; mergeable: boolean }> {
    return this.get(`/git/tasks/${taskId}/pull-request`);
  }
}
