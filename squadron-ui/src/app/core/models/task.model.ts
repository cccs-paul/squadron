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
  PLANNING = 'PLANNING',
  IN_PROGRESS = 'IN_PROGRESS',
  REVIEW = 'REVIEW',
  QA = 'QA',
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
