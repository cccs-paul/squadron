import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from './api.service';
import { UserAgentConfig, SquadronLimits } from '../models/squadron-config.model';
import { ApiResponse } from '../auth/auth.models';

@Injectable({ providedIn: 'root' })
export class UserSquadronService extends ApiService {

  /** Get the current user's agent squadron. Seeds defaults if none exist. */
  getMySquadron(): Observable<UserAgentConfig[]> {
    return this.get<ApiResponse<UserAgentConfig[]>>('/agents/squadron').pipe(
      map((response) => response.data),
    );
  }

  /** Get the configured limits (max agents per user). */
  getLimits(): Observable<SquadronLimits> {
    return this.get<ApiResponse<SquadronLimits>>('/agents/squadron/limits').pipe(
      map((response) => response.data),
    );
  }

  /** Add a new agent to the current user's squadron. */
  addAgent(agent: Partial<UserAgentConfig>): Observable<UserAgentConfig> {
    return this.post<ApiResponse<UserAgentConfig>>('/agents/squadron', agent).pipe(
      map((response) => response.data),
    );
  }

  /** Update an existing agent. */
  updateAgent(agentId: string, agent: Partial<UserAgentConfig>): Observable<UserAgentConfig> {
    return this.put<ApiResponse<UserAgentConfig>>(`/agents/squadron/${agentId}`, agent).pipe(
      map((response) => response.data),
    );
  }

  /** Remove an agent from the squadron. */
  removeAgent(agentId: string): Observable<void> {
    return this.delete<ApiResponse<void>>(`/agents/squadron/${agentId}`).pipe(
      map(() => undefined as void),
    );
  }

  /** Reset the user's squadron to defaults. */
  resetToDefaults(): Observable<UserAgentConfig[]> {
    return this.post<ApiResponse<UserAgentConfig[]>>('/agents/squadron/reset', {}).pipe(
      map((response) => response.data),
    );
  }
}
