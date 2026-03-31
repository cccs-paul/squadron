import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService, PageResponse } from './api.service';
import { Project, WorkflowMapping, WorkflowMappingsRequest } from '../models/project.model';
import { ApiResponse } from '../auth/auth.models';

@Injectable({ providedIn: 'root' })
export class ProjectService extends ApiService {
  getProjects(page = 0, size = 20, search?: string): Observable<PageResponse<Project>> {
    return this.get<PageResponse<Project>>('/projects', { page, size, ...(search ? { search } : {}) });
  }

  getProject(id: string): Observable<Project> {
    return this.get<Project>(`/projects/${id}`);
  }

  getProjectsByTenant(tenantId: string): Observable<Project[]> {
    return this.get<ApiResponse<Project[]>>(`/projects/tenant/${tenantId}`).pipe(
      map((response) => response.data),
    );
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

  // --- Workflow Mapping Methods ---

  getWorkflowMappings(projectId: string): Observable<WorkflowMapping[]> {
    return this.get<ApiResponse<WorkflowMapping[]>>(`/projects/${projectId}/workflow-mappings`).pipe(
      map((response) => response.data),
    );
  }

  saveWorkflowMappings(projectId: string, mappings: WorkflowMapping[]): Observable<WorkflowMapping[]> {
    const request: WorkflowMappingsRequest = { mappings };
    return this.put<ApiResponse<WorkflowMapping[]>>(`/projects/${projectId}/workflow-mappings`, request).pipe(
      map((response) => response.data),
    );
  }

  getWorkflowStates(): Observable<string[]> {
    return this.get<ApiResponse<string[]>>('/projects/workflow-states').pipe(
      map((response) => response.data),
    );
  }
}
