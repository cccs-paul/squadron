export interface SecurityGroup {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  members: SecurityGroupMember[];
  permissions: Permission[];
  createdAt: string;
}

export interface SecurityGroupMember {
  id: string;
  memberType: MemberType;
  memberId: string;
  memberName: string;
  addedAt: string;
}

export enum MemberType {
  USER = 'USER',
  TEAM = 'TEAM',
}

export interface Permission {
  id: string;
  tenantId: string;
  resourceType: ResourceType;
  resourceId?: string;
  granteeType: GranteeType;
  granteeId: string;
  granteeName: string;
  accessLevel: AccessLevel;
  createdAt: string;
}

export enum ResourceType {
  PROJECT = 'PROJECT',
  TASK = 'TASK',
  REVIEW = 'REVIEW',
  SETTINGS = 'SETTINGS',
  ADMIN = 'ADMIN',
}

export enum GranteeType {
  USER = 'USER',
  TEAM = 'TEAM',
  SECURITY_GROUP = 'SECURITY_GROUP',
}

export enum AccessLevel {
  READ = 'READ',
  WRITE = 'WRITE',
  ADMIN = 'ADMIN',
}

export interface AuthProvider {
  id: string;
  tenantId: string;
  name: string;
  type: AuthProviderType;
  enabled: boolean;
  config: Record<string, string>;
  createdAt: string;
}

export enum AuthProviderType {
  LDAP = 'LDAP',
  OIDC = 'OIDC',
  KEYCLOAK = 'KEYCLOAK',
  SAML = 'SAML',
}

export interface PlatformConnection {
  id: string;
  tenantId: string;
  name: string;
  platformType: PlatformConnectionType;
  baseUrl: string;
  status: ConnectionStatus;
  lastSyncAt?: string;
  config: Record<string, string>;
  createdAt: string;
}

export enum PlatformConnectionType {
  JIRA = 'JIRA',
  GITHUB = 'GITHUB',
  GITLAB = 'GITLAB',
  AZURE_DEVOPS = 'AZURE_DEVOPS',
  BITBUCKET = 'BITBUCKET',
}

export enum ConnectionStatus {
  CONNECTED = 'CONNECTED',
  DISCONNECTED = 'DISCONNECTED',
  ERROR = 'ERROR',
}
