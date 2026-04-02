/**
 * User platform token models for managing personal access tokens and OAuth2
 * links that allow Squadron to act on behalf of the user when interacting
 * with external Git/ticketing platforms.
 */

export interface UserPlatformToken {
  id: string;
  userId: string;
  connectionId: string;
  tokenType: string;          // e.g. 'PAT', 'OAUTH2'
  scopes?: string;
  expiresAt?: string;
  hasRefreshToken: boolean;
  createdAt: string;
  updatedAt?: string;
}

export interface PatLinkRequest {
  userId: string;
  connectionId: string;
  accessToken: string;
}

export interface OAuth2LinkRequest {
  userId: string;
  connectionId: string;
  authorizationCode: string;
  redirectUri: string;
}

export interface OAuth2CallbackRequest {
  userId: string;
  connectionId: string;
  authorizationCode: string;
  redirectUri: string;
  state: string;
}

export interface OAuth2AuthorizeUrlResponse {
  authorizeUrl: string;
  state: string;
}

export interface ConnectionInfo {
  id: string;
  tenantId: string;
  name: string;
  platformType: string;
  baseUrl: string;
  authType: string;
  status: string;
  createdAt: string;
}
