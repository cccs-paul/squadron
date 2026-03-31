export interface Project {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  repositoryUrl?: string;
  platformType?: PlatformType;
  platformProjectId?: string;
  connectionId?: string;
  externalProjectId?: string;
  defaultBranch: string;
  taskCount: number;
  activeTaskCount: number;
  members: string[];
  createdAt: string;
}

export enum PlatformType {
  JIRA = 'JIRA',
  GITHUB = 'GITHUB',
  GITLAB = 'GITLAB',
  AZURE_DEVOPS = 'AZURE_DEVOPS',
}

export interface WorkflowMapping {
  internalState: string;
  externalStatus: string;
}

export interface WorkflowMappingsRequest {
  mappings: WorkflowMapping[];
}
