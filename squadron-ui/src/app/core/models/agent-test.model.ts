/**
 * Agent test models — used for testing agents from the "My Agent Squadron" UI.
 * Mirrors backend DTOs in com.squadron.agent.dto.AgentTest*.
 */

/** Available test modes for agent testing. */
export type TestMode = 'PLANNING' | 'CODE_GENERATION' | 'CODE_REVIEW' | 'INTERACTIVE';

/** Labels for display in the UI. */
export const TEST_MODE_LABELS: Record<TestMode, string> = {
  PLANNING: 'Test Planning',
  CODE_GENERATION: 'Test Code Generation',
  CODE_REVIEW: 'Test Code Review',
  INTERACTIVE: 'Interactive Chat',
};

/** Icons for each test mode (used in buttons). */
export const TEST_MODE_ICONS: Record<TestMode, string> = {
  PLANNING: 'clipboard',
  CODE_GENERATION: 'code',
  CODE_REVIEW: 'search',
  INTERACTIVE: 'chat',
};

/** Request to execute an agent test. */
export interface AgentTestRequest {
  agentConfigId: string;
  testMode: TestMode;
}

/** A single log entry in the test execution timeline. */
export interface TestLogEntry {
  timestamp: string;
  phase: string;
  message: string;
  level: 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR';
}

/** Result of an agent test execution. */
export interface AgentTestResult {
  testId: string;
  agentConfigId: string;
  testMode: TestMode;
  status: 'RUNNING' | 'SUCCESS' | 'FAILURE' | 'ERROR';
  summary?: string;
  agentOutput?: string;
  durationMs?: number;
  logEntries: TestLogEntry[];
  startedAt?: string;
  completedAt?: string;
}

/** Test generator configuration (which model generates fake data). */
export interface AgentTestConfig {
  generatorProvider: string;
  generatorModel: string;
  generatorHostingType: string;
  generatorBaseUrl?: string;
  generatorApiKey?: string;
}

// ====================== Interactive Test Models ======================

/** A single message in an interactive test session. */
export interface InteractiveTestMessage {
  id: string;
  role: 'USER' | 'AGENT' | 'SYSTEM';
  content: string;
  tokenCount?: number;
  createdAt: string;
}

/** An interactive test session with an agent. */
export interface InteractiveTestSession {
  sessionId: string;
  agentConfigId: string;
  agentName: string;
  provider: string;
  model: string;
  status: 'ACTIVE' | 'STREAMING' | 'CLOSED';
  containerId: string;
  createdAt: string;
  messages: InteractiveTestMessage[];
}
