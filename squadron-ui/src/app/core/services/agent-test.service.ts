import { inject, Injectable, NgZone } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from './api.service';
import { ApiResponse } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';
import {
  AgentTestRequest,
  AgentTestResult,
  AgentTestConfig,
} from '../models/agent-test.model';

/**
 * Service for agent testing — executes tests (with SSE streaming) and manages
 * the test data generator configuration.
 */
@Injectable({ providedIn: 'root' })
export class AgentTestService extends ApiService {

  private authService = inject(AuthService);
  private ngZone = inject(NgZone);

  /**
   * Execute an agent test with SSE streaming.
   * Returns an Observable that emits AgentTestResult snapshots as log entries
   * accumulate, then completes when the test finishes.
   *
   * Uses fetch() + ReadableStream to consume the SSE stream because Angular's
   * HttpClient doesn't natively support Server-Sent Events with POST requests.
   */
  executeTest(request: AgentTestRequest): Observable<AgentTestResult> {
    return new Observable<AgentTestResult>((subscriber) => {
      const url = `${this.baseUrl}/agents/test/execute/stream`;
      const token = this.authService.getAccessToken();
      const abortController = new AbortController();

      fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(request),
        signal: abortController.signal,
      })
        .then(async (response) => {
          if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
          }

          const reader = response.body?.getReader();
          if (!reader) {
            throw new Error('No response body');
          }

          const decoder = new TextDecoder();
          let buffer = '';

          while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });

            // Parse SSE events from buffer
            const lines = buffer.split('\n');
            buffer = lines.pop() ?? ''; // keep incomplete last line

            let dataBuffer = '';
            for (const line of lines) {
              if (line.startsWith('data:')) {
                dataBuffer += line.substring(5);
              } else if (line.trim() === '' && dataBuffer) {
                // End of an SSE event — parse the JSON
                try {
                  const result = JSON.parse(dataBuffer) as AgentTestResult;
                  this.ngZone.run(() => subscriber.next(result));
                } catch {
                  // skip malformed events
                }
                dataBuffer = '';
              }
            }
          }

          this.ngZone.run(() => subscriber.complete());
        })
        .catch((err) => {
          if (err.name !== 'AbortError') {
            this.ngZone.run(() => subscriber.error(err));
          }
        });

      return () => {
        abortController.abort();
      };
    });
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
