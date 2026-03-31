/**
 * Stream chunk received from the backend WebSocket STOMP topic /topic/chat/{conversationId}.
 * Mirrors com.squadron.agent.dto.StreamChunk.
 */
export interface StreamChunk {
  conversationId: string;
  messageId?: string;
  content?: string;
  type: 'chunk' | 'done' | 'error';
  tokenCount?: number;
}

/**
 * Chat request sent to the backend STOMP destination /app/chat.
 * Mirrors com.squadron.agent.dto.ChatRequest.
 */
export interface ChatRequest {
  conversationId?: string;
  taskId: string;
  agentType: string;
  message: string;
}

// --- Agent Dashboard Models ---
// Mirrors DTOs from com.squadron.agent.dto.*

/** Top-level dashboard response. Mirrors AgentDashboardDto. */
export interface AgentDashboard {
  activeAgents: number;
  idleAgents: number;
  totalConversations: number;
  totalTokensUsed: number;
  activeWork: ActiveAgentWork[];
  recentActivity: AgentActivity[];
  agentTypeSummaries: AgentTypeSummary[];
}

/** A currently active agent conversation. Mirrors ActiveAgentWorkDto. */
export interface ActiveAgentWork {
  conversationId: string;
  taskId: string;
  agentType: string;
  status: string;
  provider: string;
  model: string;
  totalTokens: number;
  startedAt: string;
  lastActivityAt: string;
}

/** An item in the recent activity timeline. Mirrors AgentActivityDto. */
export interface AgentActivity {
  conversationId: string;
  taskId: string;
  agentType: string;
  action: string;
  totalTokens: number;
  timestamp: string;
}

/** Per-agent-type breakdown. Mirrors AgentTypeSummaryDto. */
export interface AgentTypeSummary {
  agentType: string;
  activeCount: number;
  completedCount: number;
  totalTokens: number;
}
