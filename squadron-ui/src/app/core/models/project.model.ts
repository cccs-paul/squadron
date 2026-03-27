export interface Project {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  repositoryUrl?: string;
  platformType?: PlatformType;
  platformProjectId?: string;
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
