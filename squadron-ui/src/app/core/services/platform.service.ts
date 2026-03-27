import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { PlatformConnection, PlatformConnectionType } from '../models/security.model';

@Injectable({ providedIn: 'root' })
export class PlatformService extends ApiService {
  getConnections(): Observable<PlatformConnection[]> {
    return this.get<PlatformConnection[]>('/platforms/connections');
  }

  getConnection(id: string): Observable<PlatformConnection> {
    return this.get<PlatformConnection>(`/platforms/connections/${id}`);
  }

  createConnection(connection: Partial<PlatformConnection>): Observable<PlatformConnection> {
    return this.post<PlatformConnection>('/platforms/connections', connection);
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
}
