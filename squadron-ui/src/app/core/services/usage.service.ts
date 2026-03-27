import { Injectable } from '@angular/core';
import { ApiService } from './api.service';
import { UsageSummary, UsageByAgent } from '../models/usage.model';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class UsageService extends ApiService {
  getTenantSummary(tenantId: string, start?: string, end?: string): Observable<UsageSummary> {
    const params: Record<string, string> = {};
    if (start) params['start'] = start;
    if (end) params['end'] = end;
    return this.get<UsageSummary>(`/agent/usage/tenant/${tenantId}`, params);
  }

  getUserSummary(tenantId: string, userId: string): Observable<UsageSummary> {
    return this.get<UsageSummary>(`/agent/usage/tenant/${tenantId}/user/${userId}`);
  }

  getTeamSummary(tenantId: string, teamId: string): Observable<UsageSummary> {
    return this.get<UsageSummary>(`/agent/usage/tenant/${tenantId}/team/${teamId}`);
  }

  getByAgentType(tenantId: string): Observable<UsageByAgent[]> {
    return this.get<UsageByAgent[]>(`/agent/usage/tenant/${tenantId}/by-agent`);
  }
}
