import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from './api.service';
import { PlatformConnection, PlatformConnectionType, CreateConnectionRequest } from '../models/security.model';
import { ApiResponse } from '../auth/auth.models';

@Injectable({ providedIn: 'root' })
export class PlatformService extends ApiService {
  getConnections(): Observable<PlatformConnection[]> {
    return this.get<PlatformConnection[]>('/platforms/connections');
  }

  getConnectionsByTenant(tenantId: string): Observable<PlatformConnection[]> {
    return this.get<ApiResponse<PlatformConnection[]>>(`/platforms/connections/tenant/${tenantId}`).pipe(
      map((response) => response.data),
    );
  }

  getConnection(id: string): Observable<PlatformConnection> {
    return this.get<PlatformConnection>(`/platforms/connections/${id}`);
  }

  createConnection(connection: Partial<PlatformConnection>): Observable<PlatformConnection> {
    return this.post<PlatformConnection>('/platforms/connections', connection);
  }

  createConnectionFromRequest(request: CreateConnectionRequest): Observable<PlatformConnection> {
    return this.post<ApiResponse<PlatformConnection>>('/platforms/connections', request).pipe(
      map((response) => response.data),
    );
  }

  updateConnection(id: string, connection: Partial<PlatformConnection>): Observable<PlatformConnection> {
    return this.put<PlatformConnection>(`/platforms/connections/${id}`, connection);
  }

  deleteConnection(id: string): Observable<void> {
    return this.delete<void>(`/platforms/connections/${id}`);
  }

  testConnection(id: string): Observable<{ success: boolean; message: string }> {
    return this.post<{ success: boolean; message: string }>(`/platforms/connections/${id}/test`, {});
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
}
