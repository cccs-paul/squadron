export interface User {
  id: string;
  tenantId: string;
  username: string;
  email: string;
  displayName: string;
  avatarUrl?: string;
  role: UserRole;
  teams: string[];
  status: UserStatus;
  lastLoginAt?: string;
  createdAt: string;
}

export enum UserRole {
  ADMIN = 'ADMIN',
  MANAGER = 'MANAGER',
  DEVELOPER = 'DEVELOPER',
  VIEWER = 'VIEWER',
}

export enum UserStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
  SUSPENDED = 'SUSPENDED',
}

export interface Team {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  memberCount: number;
  members: User[];
  createdAt: string;
}
