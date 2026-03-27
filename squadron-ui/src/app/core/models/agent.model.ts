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
