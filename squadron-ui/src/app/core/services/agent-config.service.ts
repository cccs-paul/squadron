import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

export interface AgentConfig {
  id?: string;
  tenantId: string;
  provider: string;
  modelName: string;
  temperature: number;
  maxTokens: number;
  systemPrompt?: string;
  agentOverrides?: Record<string, Partial<AgentConfig>>;
}

@Injectable({ providedIn: 'root' })
export class AgentConfigService extends ApiService {
  getConfig(tenantId: string): Observable<AgentConfig> {
    return this.get<AgentConfig>(`/agent/config/${tenantId}`);
  }

  updateConfig(tenantId: string, config: Partial<AgentConfig>): Observable<AgentConfig> {
    return this.put<AgentConfig>(`/agent/config/${tenantId}`, config);
  }
}
