export interface DiffFile {
  filename: string;
  status: string; // 'added' | 'modified' | 'deleted' | 'renamed'
  additions: number;
  deletions: number;
  patch: string;
}

export interface DiffResult {
  files: DiffFile[];
  totalAdditions: number;
  totalDeletions: number;
}

export interface CodeGenerationStatus {
  taskId: string;
  status: string; // 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'
  branchName: string;
  prUrl?: string;
  prId?: string;
  summary?: string;
  error?: string;
}
