import { inject, Injectable, NgZone } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from './api.service';
import { ApiResponse } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';
import {
  AgentTestRequest,
  AgentTestResult,
  AgentTestConfig,
  InteractiveTestSession,
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
    return this.streamSse<AgentTestResult>(
      `${this.baseUrl}/agents/test/execute/stream`,
      request,
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

  // ====================== Interactive Test Methods ======================

  /** Start a new interactive test session for the given agent config (SSE streaming). */
  startInteractiveSession(agentConfigId: string): Observable<InteractiveTestSession> {
    return this.streamSse<InteractiveTestSession>(
      `${this.baseUrl}/agents/test/interactive/start?agentConfigId=${agentConfigId}`,
      {},
    );
  }

  /**
   * Send a message to an interactive test session and stream the response via SSE.
   * Returns an Observable that emits InteractiveTestSession snapshots as the agent
   * streams its response, then completes.
   */
  sendInteractiveMessage(sessionId: string, message: string): Observable<InteractiveTestSession> {
    return this.streamSse<InteractiveTestSession>(
      `${this.baseUrl}/agents/test/interactive/message/stream`,
      { sessionId, message },
    );
  }

  /** Get the current state of an interactive test session. */
  getInteractiveSession(sessionId: string): Observable<InteractiveTestSession> {
    return this.get<ApiResponse<InteractiveTestSession>>(
      `/agents/test/interactive/${sessionId}`,
    ).pipe(map((response) => response.data));
  }

  /** List all active interactive test sessions for the current user. */
  getInteractiveSessions(): Observable<InteractiveTestSession[]> {
    return this.get<ApiResponse<InteractiveTestSession[]>>(
      '/agents/test/interactive/sessions',
    ).pipe(map((response) => response.data));
  }

  /** Close an interactive test session. */
  closeInteractiveSession(sessionId: string): Observable<void> {
    return this.delete<void>(`/agents/test/interactive/${sessionId}`);
  }

  // ====================== Private Helpers ======================

  /**
   * Streams SSE events from a POST endpoint using fetch(). Handles:
   * - 401 Unauthorized: automatically refreshes the JWT token and retries once
   * - Other HTTP errors: throws with a user-friendly message
   * - SSE parsing: emits parsed JSON objects as they arrive
   */
  private streamSse<T>(url: string, body: unknown): Observable<T> {
    return new Observable<T>((subscriber) => {
      const abortController = new AbortController();
      let retried = false;

      const doFetch = (token: string | null): void => {
        fetch(url, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          body: JSON.stringify(body),
          signal: abortController.signal,
        })
          .then(async (response) => {
            // On 401, try refreshing the token once
            if (response.status === 401 && !retried) {
              retried = true;
              this.authService.refreshToken().subscribe({
                next: (result) => {
                  if (result) {
                    doFetch(this.authService.getAccessToken());
                  } else {
                    this.ngZone.run(() =>
                      subscriber.error(new Error(
                        'Your session has expired. Please log in again.')));
                  }
                },
                error: () => {
                  this.ngZone.run(() =>
                    subscriber.error(new Error(
                      'Your session has expired. Please log in again.')));
                },
              });
              return;
            }

            if (!response.ok) {
              throw new Error(this.friendlyHttpError(response.status, response.statusText));
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

              const lines = buffer.split('\n');
              buffer = lines.pop() ?? '';

              let dataBuffer = '';
              for (const line of lines) {
                if (line.startsWith('data:')) {
                  dataBuffer += line.substring(5);
                } else if (line.trim() === '' && dataBuffer) {
                  try {
                    const parsed = JSON.parse(dataBuffer) as T;
                    this.ngZone.run(() => subscriber.next(parsed));
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
      };

      doFetch(this.authService.getAccessToken());

      return () => {
        abortController.abort();
      };
    });
  }

  /** Maps HTTP status codes to user-friendly error messages. */
  private friendlyHttpError(status: number, statusText: string): string {
    switch (status) {
      case 401:
        return 'Your session has expired. Please log in again.';
      case 403:
        return 'You do not have permission to perform this action.';
      case 404:
        return 'The requested resource was not found. The session may have expired.';
      case 429:
        return 'Too many requests. Please wait a moment and try again.';
      case 502:
      case 503:
        return 'The server is temporarily unavailable. Please try again in a few moments.';
      case 504:
        return 'The request timed out. The server may be under heavy load.';
      default:
        return `Request failed (HTTP ${status}: ${statusText}). Please try again.`;
    }
  }
}
