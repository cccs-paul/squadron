/**
 * Stream chunk received from the backend WebSocket STOMP topic /topic/chat/{conversationId}.
 * Mirrors com.squadron.agent.dto.StreamChunk.
 */
export interface StreamChunk {
  conversationId: string;
  messageId?: string;
  content?: string;
  type: 'chunk' | 'done' | 'error' | 'interrupted';
  tokenCount?: number;
}

/**
 * Agent progress update received via WebSocket on /topic/progress/{conversationId}
 * or via REST GET /api/agents/sessions/{id}/progress.
 * Mirrors com.squadron.agent.dto.AgentProgressDto.
 */
export interface AgentProgress {
  conversationId: string;
  agentType: string;
  phase: string;       // e.g. "PLANNING", "CODING", "REVIEWING", "TESTING"
  currentStep: string; // e.g. "Analyzing codebase", "Writing tests"
  completedSteps: number;
  totalSteps: number;
  items: ProgressItem[];
}

/** A single TODO/progress item within AgentProgress. */
export interface ProgressItem {
  content: string;
  status: 'pending' | 'in_progress' | 'completed';
  priority: 'high' | 'medium' | 'low';
}

/**
 * Request to interrupt/cancel a running agent session.
 * Mirrors com.squadron.agent.dto.AgentInterruptRequest.
 */
export interface AgentInterruptRequest {
  conversationId: string;
  reason: 'USER_CANCEL' | 'USER_PROMPT' | 'TIMEOUT';
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
