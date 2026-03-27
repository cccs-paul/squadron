export interface UsageSummary {
  totalInputTokens: number;
  totalOutputTokens: number;
  totalTokens: number;
  totalCost: number;
  invocations: number;
}

export interface UsageByAgent {
  agentType: string;
  totalTokens: number;
  totalCost: number;
  invocations: number;
}
