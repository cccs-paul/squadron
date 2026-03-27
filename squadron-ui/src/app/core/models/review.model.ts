export interface Review {
  id: string;
  tenantId: string;
  taskId: string;
  taskTitle: string;
  pullRequestUrl: string;
  pullRequestNumber: number;
  repositoryName: string;
  status: ReviewStatus;
  severity: ReviewSeverity;
  comments: ReviewComment[];
  filesChanged: number;
  linesAdded: number;
  linesRemoved: number;
  reviewerType: ReviewerType;
  createdAt: string;
  updatedAt: string;
}

export enum ReviewStatus {
  PENDING = 'PENDING',
  IN_PROGRESS = 'IN_PROGRESS',
  APPROVED = 'APPROVED',
  CHANGES_REQUESTED = 'CHANGES_REQUESTED',
  REJECTED = 'REJECTED',
}

export enum ReviewSeverity {
  CRITICAL = 'CRITICAL',
  MAJOR = 'MAJOR',
  MINOR = 'MINOR',
  INFO = 'INFO',
}

export enum ReviewerType {
  AI = 'AI',
  HUMAN = 'HUMAN',
}

export interface ReviewComment {
  id: string;
  filePath: string;
  lineNumber?: number;
  body: string;
  severity: ReviewSeverity;
  category: ReviewCategory;
  resolved: boolean;
  authorName: string;
  authorType: ReviewerType;
  createdAt: string;
}

export enum ReviewCategory {
  BUG = 'BUG',
  SECURITY = 'SECURITY',
  PERFORMANCE = 'PERFORMANCE',
  STYLE = 'STYLE',
  BEST_PRACTICE = 'BEST_PRACTICE',
  DOCUMENTATION = 'DOCUMENTATION',
  TESTING = 'TESTING',
}
