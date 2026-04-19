import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from './api.service';
import { ApiResponse } from '../auth/auth.models';
import {
  AgentTestRequest,
  AgentTestResult,
  AgentTestConfig,
} from '../models/agent-test.model';

/**
 * Service for agent testing — executes tests and manages
 * the test data generator configuration.
 */
@Injectable({ providedIn: 'root' })
export class AgentTestService extends ApiService {

  /** Execute an agent test (planning, code generation, or code review). */
  executeTest(request: AgentTestRequest): Observable<AgentTestResult> {
    return this.post<ApiResponse<AgentTestResult>>('/agents/test/execute', request).pipe(
      map((response) => response.data),
    );
  }

  /** Get the user's test data generator configuration. */
  getTestConfig(): Observable<AgentTestConfig> {
    return this.get<ApiResponse<AgentTestConfig>>('/agents/test/config').pipe(
      map((response) => response.data),
    );
  }

  /** Update the user's test data generator configuration. */
  updateTestConfig(config: AgentTestConfig): Observable<AgentTestConfig> {
    return this.put<ApiResponse<AgentTestConfig>>('/agents/test/config', config).pipe(
      map((response) => response.data),
    );
  }
}
