import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService, PageResponse } from './api.service';
import { Project } from '../models/project.model';

@Injectable({ providedIn: 'root' })
export class ProjectService extends ApiService {
  getProjects(page = 0, size = 20, search?: string): Observable<PageResponse<Project>> {
    return this.get<PageResponse<Project>>('/projects', { page, size, ...(search ? { search } : {}) });
  }

  getProject(id: string): Observable<Project> {
    return this.get<Project>(`/projects/${id}`);
  }

  createProject(project: Partial<Project>): Observable<Project> {
    return this.post<Project>('/projects', project);
  }

  updateProject(id: string, project: Partial<Project>): Observable<Project> {
    return this.put<Project>(`/projects/${id}`, project);
  }

  deleteProject(id: string): Observable<void> {
    return this.delete<void>(`/projects/${id}`);
  }
}
