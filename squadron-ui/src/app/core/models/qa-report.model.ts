export interface QAReport {
  id: string;
  tenantId: string;
  taskId: string;
  verdict: QAVerdict;
  summary: string;
  coveragePercentage: number;
  testsPassed: number;
  testsFailed: number;
  testsSkipped: number;
  findings: QAFinding[];
  gateResult: string;
  createdAt: string;
}

export enum QAVerdict {
  PASS = 'PASS',
  CONDITIONAL_PASS = 'CONDITIONAL_PASS',
  FAIL = 'FAIL',
}

export interface QAFinding {
  type: string;
  message: string;
  filePath?: string;
  lineNumber?: number;
  severity: string;
}

export interface QAGateResult {
  passed: boolean;
  verdict: QAVerdict;
  coveragePercentage: number;
  testsPassed: number;
  testsFailed: number;
}
