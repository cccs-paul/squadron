import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from './api.service';
import { PlatformConnection, PlatformConnectionType, CreateConnectionRequest } from '../models/security.model';
import { RemoteProject } from '../models/project.model';
import { ApiResponse } from '../auth/auth.models';

@Injectable({ providedIn: 'root' })
export class PlatformService extends ApiService {
  getConnections(tenantId: string): Observable<PlatformConnection[]> {
    return this.get<ApiResponse<PlatformConnection[]>>(`/platforms/connections/tenant/${tenantId}`).pipe(
      map((response) => response.data),
    );
  }

  getConnectionsByTenant(tenantId: string): Observable<PlatformConnection[]> {
    return this.get<ApiResponse<PlatformConnection[]>>(`/platforms/connections/tenant/${tenantId}`).pipe(
      map((response) => response.data),
    );
  }

  /** Get connections filtered by category (TICKET_PROVIDER or GIT_REMOTE). */
  getConnectionsByCategory(tenantId: string, category: string): Observable<PlatformConnection[]> {
    return this.get<ApiResponse<PlatformConnection[]>>(
      `/platforms/connections/tenant/${tenantId}/category/${category}`,
    ).pipe(
      map((response) => response.data),
    );
  }

  getConnection(id: string): Observable<PlatformConnection> {
    return this.get<ApiResponse<PlatformConnection>>(`/platforms/connections/${id}`).pipe(
      map((response) => response.data),
    );
  }

  createConnection(connection: Partial<PlatformConnection>): Observable<PlatformConnection> {
    return this.post<ApiResponse<PlatformConnection>>('/platforms/connections', connection).pipe(
      map((response) => response.data),
    );
  }

  createConnectionFromRequest(request: CreateConnectionRequest): Observable<PlatformConnection> {
    return this.post<ApiResponse<PlatformConnection>>('/platforms/connections', request).pipe(
      map((response) => response.data),
    );
  }

  updateConnection(id: string, connection: Partial<PlatformConnection>): Observable<PlatformConnection> {
    return this.put<ApiResponse<PlatformConnection>>(`/platforms/connections/${id}`, connection).pipe(
      map((response) => response.data),
    );
  }

  deleteConnection(id: string): Observable<void> {
    return this.delete<void>(`/platforms/connections/${id}`);
  }

  testConnection(id: string): Observable<boolean> {
    return this.post<ApiResponse<boolean>>(`/platforms/connections/${id}/test`, {}).pipe(
      map((response) => response.data),
    );
  }

  syncConnection(id: string): Observable<void> {
    return this.post<void>(`/platforms/connections/${id}/sync`, {});
  }

  /**
   * Fetches available workflow statuses from the remote ticketing platform
   * for a given connection and project key.
   */
  getProjectStatuses(connectionId: string, projectKey: string): Observable<string[]> {
    return this.get<ApiResponse<string[]>>(
      `/platforms/connections/${connectionId}/statuses`,
      { projectKey },
    ).pipe(
      map((response) => response.data),
    );
  }

  /**
   * Fetches the list of projects/repositories from the remote ticketing platform
   * for a given connection. Used to populate the project import UI.
   */
  getRemoteProjects(connectionId: string): Observable<RemoteProject[]> {
    return this.get<ApiResponse<RemoteProject[]>>(
      `/platforms/connections/${connectionId}/projects`,
    ).pipe(
      map((response) => response.data),
    );
  }
}
