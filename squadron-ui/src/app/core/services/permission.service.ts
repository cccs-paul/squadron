import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService, PageResponse } from './api.service';
import { Permission, AuthProvider } from '../models/security.model';

@Injectable({ providedIn: 'root' })
export class PermissionService extends ApiService {
  getPermissions(page = 0, size = 50): Observable<PageResponse<Permission>> {
    return this.get<PageResponse<Permission>>('/permissions', { page, size });
  }

  grantPermission(permission: Partial<Permission>): Observable<Permission> {
    return this.post<Permission>('/permissions', permission);
  }

  revokePermission(id: string): Observable<void> {
    return this.delete<void>(`/permissions/${id}`);
  }

  getAuthProviders(): Observable<AuthProvider[]> {
    return this.get<AuthProvider[]>('/auth-providers');
  }

  getAuthProvider(id: string): Observable<AuthProvider> {
    return this.get<AuthProvider>(`/auth-providers/${id}`);
  }

  createAuthProvider(provider: Partial<AuthProvider>): Observable<AuthProvider> {
    return this.post<AuthProvider>('/auth-providers', provider);
  }

  updateAuthProvider(id: string, provider: Partial<AuthProvider>): Observable<AuthProvider> {
    return this.put<AuthProvider>(`/auth-providers/${id}`, provider);
  }

  deleteAuthProvider(id: string): Observable<void> {
    return this.delete<void>(`/auth-providers/${id}`);
  }

  testAuthProvider(id: string): Observable<{ success: boolean; message: string }> {
    return this.post<{ success: boolean; message: string }>(`/auth-providers/${id}/test`, {});
  }
}
