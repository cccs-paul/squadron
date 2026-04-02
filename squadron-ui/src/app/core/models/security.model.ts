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
  platformCategory?: string;
  baseUrl: string;
  authType?: string;
  status: ConnectionStatus;
  lastSyncAt?: string;
  config: Record<string, string>;
  createdAt: string;
}

export enum PlatformConnectionType {
  JIRA_CLOUD = 'JIRA_CLOUD',
  JIRA_SERVER = 'JIRA_SERVER',
  GITHUB = 'GITHUB',
  GITLAB = 'GITLAB',
  AZURE_DEVOPS = 'AZURE_DEVOPS',
  BITBUCKET = 'BITBUCKET',
}

export enum ConnectionStatus {
  ACTIVE = 'ACTIVE',
  ERROR = 'ERROR',
}

export enum PlatformCategory {
  TICKET_PROVIDER = 'TICKET_PROVIDER',
  GIT_REMOTE = 'GIT_REMOTE',
}

export interface CreateConnectionRequest {
  tenantId: string;
  name: string;
  platformType: string;
  baseUrl: string;
  authType: string;
  credentials: Record<string, string>;
  metadata?: Record<string, unknown>;
}

/** SSH key linked to a platform connection for Git clone/push operations. */
export interface SshKey {
  id: string;
  tenantId: string;
  connectionId: string;
  name: string;
  publicKey: string;
  fingerprint: string;
  keyType: string;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateSshKeyRequest {
  tenantId: string;
  connectionId: string;
  name: string;
  publicKey: string;
  privateKey: string;
  keyType?: string;
}
