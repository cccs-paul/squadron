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
  branchNamingTemplate?: string;
  taskCount: number;
  activeTaskCount: number;
  members: string[];
  createdAt: string;
}

export enum PlatformType {
  JIRA_CLOUD = 'JIRA_CLOUD',
  JIRA_SERVER = 'JIRA_SERVER',
  GITHUB = 'GITHUB',
  GITLAB = 'GITLAB',
  AZURE_DEVOPS = 'AZURE_DEVOPS',
  BITBUCKET = 'BITBUCKET',
}

export enum BranchStrategyType {
  TRUNK_BASED = 'TRUNK_BASED',
  GITFLOW = 'GITFLOW',
  GITHUB_FLOW = 'GITHUB_FLOW',
  GITLAB_FLOW = 'GITLAB_FLOW',
  RELEASE_BRANCHING = 'RELEASE_BRANCHING',
}

export interface WorkflowMapping {
  internalState: string;
  externalStatus: string;
}

export interface WorkflowMappingsRequest {
  mappings: WorkflowMapping[];
}

export interface RemoteProject {
  key: string;
  name: string;
  description?: string;
  url?: string;
  avatarUrl?: string;
}
