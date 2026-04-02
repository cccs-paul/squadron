import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from './api.service';
import {
  UserPlatformToken,
  PatLinkRequest,
  OAuth2LinkRequest,
  OAuth2CallbackRequest,
  OAuth2AuthorizeUrlResponse,
  ConnectionInfo,
} from '../models/user-token.model';
import { ApiResponse } from '../auth/auth.models';

@Injectable({ providedIn: 'root' })
export class UserTokenService extends ApiService {

  /** Link an account using an OAuth2 authorization code. */
  linkOAuth2Account(request: OAuth2LinkRequest): Observable<UserPlatformToken> {
    return this.post<ApiResponse<UserPlatformToken>>('/platforms/tokens/oauth2/link', request).pipe(
      map((response) => response.data),
    );
  }

  /** Link an account using a personal access token. */
  linkPatAccount(request: PatLinkRequest): Observable<UserPlatformToken> {
    return this.post<ApiResponse<UserPlatformToken>>('/platforms/tokens/pat/link', request).pipe(
      map((response) => response.data),
    );
  }

  /** Get all platform tokens for a given user. */
  getTokensByUser(userId: string): Observable<UserPlatformToken[]> {
    return this.get<ApiResponse<UserPlatformToken[]>>(`/platforms/tokens/user/${userId}`).pipe(
      map((response) => response.data),
    );
  }

  /** Unlink (delete) a user's token for a specific connection. */
  unlinkAccount(userId: string, connectionId: string): Observable<void> {
    return this.delete<void>(`/platforms/tokens/user/${userId}/connection/${connectionId}`);
  }

  /** Get available platform connections for a tenant. */
  getAvailableConnections(tenantId: string): Observable<ConnectionInfo[]> {
    return this.get<ApiResponse<ConnectionInfo[]>>(`/platforms/tokens/connections/${tenantId}`).pipe(
      map((response) => response.data),
    );
  }

  /** Get the OAuth2 authorization URL for a given connection. */
  getOAuth2AuthorizeUrl(connectionId: string): Observable<OAuth2AuthorizeUrlResponse> {
    return this.get<ApiResponse<OAuth2AuthorizeUrlResponse>>(`/platforms/tokens/oauth2/authorize/${connectionId}`).pipe(
      map((response) => response.data),
    );
  }

  /** Handle the OAuth2 callback after user authorization. */
  handleOAuth2Callback(request: OAuth2CallbackRequest): Observable<UserPlatformToken> {
    return this.post<ApiResponse<UserPlatformToken>>('/platforms/tokens/oauth2/callback', request).pipe(
      map((response) => response.data),
    );
  }
}
