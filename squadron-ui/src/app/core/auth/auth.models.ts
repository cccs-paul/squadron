export interface AuthUser {
  id: string;
  username: string;
  email: string;
  displayName: string;
  avatarUrl?: string;
  tenantId: string;
  tenantName: string;
  roles: string[];
  permissions: string[];
}

export interface LoginRequest {
  username: string;
  password: string;
  tenantSlug?: string;
  rememberMe?: boolean;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  user: AuthUser;
}

export interface OidcConfig {
  authority: string;
  clientId: string;
  redirectUri: string;
  scope: string;
  responseType: string;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}
