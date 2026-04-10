export interface Task {
  id: string;
  tenantId: string;
  projectId: string;
  externalId?: string;
  externalUrl?: string;
  title: string;
  description?: string;
  state: TaskState;
  priority: TaskPriority;
  assigneeId?: string;
  assigneeName?: string;
  assigneeAvatar?: string;
  labels: string[];
  pullRequestUrl?: string;
  reviewId?: string;
  agentSessionId?: string;
  tokenUsage: number;
  createdAt: string;
  updatedAt: string;
}

export enum TaskState {
  BACKLOG = 'BACKLOG',
  PRIORITIZED = 'PRIORITIZED',
  PLANNING = 'PLANNING',
  PROPOSE_CODE = 'PROPOSE_CODE',
  IN_PROGRESS = 'IN_PROGRESS',
  REVIEW = 'REVIEW',
  QA = 'QA',
  MERGE = 'MERGE',
  DONE = 'DONE',
}

export enum TaskPriority {
  CRITICAL = 'CRITICAL',
  HIGH = 'HIGH',
  MEDIUM = 'MEDIUM',
  LOW = 'LOW',
}

export interface TaskStateTransition {
  fromState: TaskState;
  toState: TaskState;
  allowed: boolean;
}

export interface TaskFilter {
  state?: TaskState;
  priority?: TaskPriority;
  assigneeId?: string;
  projectId?: string;
  search?: string;
}

export interface TaskSyncRequest {
  tenantId: string;
  teamId?: string;
  projectId: string;
  platformConnectionId: string;
  projectKey: string;
}

export interface TaskSyncResult {
  created: number;
  updated: number;
  unchanged: number;
  failed: number;
  errors: string[];
}

/** Detailed task with workflow state, transitions, and project context */
export interface TaskDetail {
  id: string;
  tenantId: string;
  projectId: string;
  teamId?: string;
  assigneeId?: string;
  title: string;
  description?: string;
  externalId?: string;
  externalUrl?: string;
  priority: TaskPriority;
  labels: string[];
  tokenUsage: number;
  currentState: TaskState;
  previousState?: TaskState;
  lastTransitionAt?: string;
  availableTransitions: TaskState[];
  projectName?: string;
  mappedExternalStatus?: string;
  createdAt: string;
  updatedAt: string;
}

/** Request to delegate a task to an AI agent */
export interface DelegateTaskRequest {
  agentType: string;
  agentName?: string;
  instructions?: string;
  targetState?: string;
}
