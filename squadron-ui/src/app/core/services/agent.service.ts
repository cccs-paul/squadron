import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { StreamChunk, ChatRequest } from '../models/agent.model';
import type { WebSocketService } from './websocket.service';

export interface AgentMessage {
  id: string;
  sessionId: string;
  role: 'USER' | 'AGENT' | 'SYSTEM';
  content: string;
  tokenUsage?: number;
  createdAt: string;
}

export interface AgentSession {
  id: string;
  taskId: string;
  status: 'ACTIVE' | 'WAITING_INPUT' | 'COMPLETED' | 'FAILED';
  totalTokens: number;
  messages: AgentMessage[];
  currentPlan?: AgentPlan;
  createdAt: string;
}

export interface AgentPlan {
  id: string;
  steps: AgentPlanStep[];
  approved: boolean;
  createdAt: string;
}

export interface AgentPlanStep {
  id: string;
  description: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
  order: number;
}

@Injectable({ providedIn: 'root' })
export class AgentService extends ApiService {

  getSession(taskId: string): Observable<AgentSession> {
    return this.get<AgentSession>(`/agent/sessions/task/${taskId}`);
  }

  sendMessage(sessionId: string, message: string): Observable<AgentMessage> {
    return this.post<AgentMessage>(`/agent/sessions/${sessionId}/messages`, { content: message });
  }

  approvePlan(sessionId: string, planId: string): Observable<void> {
    return this.post<void>(`/agent/sessions/${sessionId}/plans/${planId}/approve`, {});
  }

  rejectPlan(sessionId: string, planId: string, feedback: string): Observable<void> {
    return this.post<void>(`/agent/sessions/${sessionId}/plans/${planId}/reject`, { feedback });
  }

  getMessages(sessionId: string): Observable<AgentMessage[]> {
    return this.get<AgentMessage[]>(`/agent/sessions/${sessionId}/messages`);
  }

  /**
   * Subscribe to streaming chunks for a specific conversation.
   * Returns an Observable<StreamChunk> that emits each chunk, done, or error event.
   */
  subscribeToStream(conversationId: string, wsService: WebSocketService): Observable<StreamChunk> {
    return wsService.subscribe<StreamChunk>(`/topic/chat/${conversationId}`);
  }

  /**
   * Send a chat message via WebSocket STOMP to /app/chat for streaming response.
   */
  sendStreamingMessage(request: ChatRequest, wsService: WebSocketService): void {
    wsService.publish('/app/chat', request);
  }
}
