import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from './api.service';
import { AgentDashboard } from '../models/agent.model';
import { ApiResponse } from '../auth/auth.models';

@Injectable({ providedIn: 'root' })
export class AgentDashboardService extends ApiService {
  getDashboard(): Observable<AgentDashboard> {
    return this.get<ApiResponse<AgentDashboard>>('/agents/dashboard').pipe(
      map((response) => response.data),
    );
  }
}
