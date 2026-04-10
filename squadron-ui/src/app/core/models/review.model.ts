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
  summary?: string;
  comments: ReviewComment[];
  filesChanged: number;
  linesAdded: number;
  linesRemoved: number;
  reviewerType: ReviewerType;
  reviewerId?: string;
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

/** Summary of all reviews for a task, including policy gate status. */
export interface ReviewSummary {
  taskId: string;
  totalReviews: number;
  humanApprovals: number;
  aiApproval: boolean;
  policyMet: boolean;
  reviews: Review[];
}

/** QA report with coverage, test results, and findings. */
export interface QAReport {
  id: string;
  tenantId: string;
  taskId: string;
  verdict: QAVerdict;
  summary?: string;
  lineCoverage?: number;
  branchCoverage?: number;
  testsPassed?: number;
  testsFailed?: number;
  testsSkipped?: number;
  findings?: string;
  testGaps?: string;
  coverageDetails?: string;
  createdAt: string;
}

export enum QAVerdict {
  PASS = 'PASS',
  CONDITIONAL_PASS = 'CONDITIONAL_PASS',
  FAIL = 'FAIL',
}

/** Result of triggering a review orchestration for a task. */
export interface ReviewOrchestrationResult {
  taskId: string;
  createdReviewIds: string[];
  aiReviewCreated: boolean;
  pendingHumanReviews: number;
  policyMet: boolean;
}

/** Request to submit a review with status, summary, and comments. */
export interface SubmitReviewRequest {
  reviewId: string;
  status: ReviewStatus;
  summary?: string;
  comments?: { filePath: string; lineNumber?: number; body: string; severity: string; category: string }[];
}
