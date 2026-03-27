export interface Tenant {
  id: string;
  name: string;
  slug: string;
  plan: TenantPlan;
  settings: TenantSettings;
  createdAt: string;
}

export enum TenantPlan {
  FREE = 'FREE',
  TEAM = 'TEAM',
  ENTERPRISE = 'ENTERPRISE',
}

export interface TenantSettings {
  maxUsers: number;
  maxProjects: number;
  aiEnabled: boolean;
  defaultBranch: string;
  autoReview: boolean;
}
