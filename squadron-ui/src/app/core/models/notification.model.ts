export interface Notification {
  id: string;
  tenantId: string;
  userId: string;
  type: NotificationType;
  title: string;
  message: string;
  link?: string;
  read: boolean;
  createdAt: string;
}

export enum NotificationType {
  TASK_ASSIGNED = 'TASK_ASSIGNED',
  TASK_STATE_CHANGED = 'TASK_STATE_CHANGED',
  REVIEW_REQUESTED = 'REVIEW_REQUESTED',
  REVIEW_COMPLETED = 'REVIEW_COMPLETED',
  AGENT_COMPLETED = 'AGENT_COMPLETED',
  AGENT_NEEDS_INPUT = 'AGENT_NEEDS_INPUT',
  SYSTEM = 'SYSTEM',
}
