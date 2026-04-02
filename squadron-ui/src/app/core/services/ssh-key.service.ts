/**
 * Service for managing SSH keys linked to platform connections.
 * SSH keys are used for Git clone/push operations against remote repositories.
 */
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from './api.service';
import { SshKey, CreateSshKeyRequest } from '../models/security.model';
import { ApiResponse } from '../auth/auth.models';

@Injectable({ providedIn: 'root' })
export class SshKeyService extends ApiService {

  /** Create a new SSH key for a platform connection. */
  createSshKey(request: CreateSshKeyRequest): Observable<SshKey> {
    return this.post<ApiResponse<SshKey>>('/platforms/ssh-keys', request).pipe(
      map((response) => response.data),
    );
  }

  /** Get an SSH key by its ID. */
  getSshKey(id: string): Observable<SshKey> {
    return this.get<ApiResponse<SshKey>>(`/platforms/ssh-keys/${id}`).pipe(
      map((response) => response.data),
    );
  }

  /** Get all SSH keys for a tenant. */
  getSshKeysByTenant(tenantId: string): Observable<SshKey[]> {
    return this.get<ApiResponse<SshKey[]>>(`/platforms/ssh-keys/tenant/${tenantId}`).pipe(
      map((response) => response.data),
    );
  }

  /** Get all SSH keys for a specific connection. */
  getSshKeysByConnection(connectionId: string): Observable<SshKey[]> {
    return this.get<ApiResponse<SshKey[]>>(`/platforms/ssh-keys/connection/${connectionId}`).pipe(
      map((response) => response.data),
    );
  }

  /** Delete an SSH key. */
  deleteSshKey(id: string): Observable<void> {
    return this.delete<void>(`/platforms/ssh-keys/${id}`);
  }
}
