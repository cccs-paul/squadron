import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { UserTokenService } from './user-token.service';
import {
  UserPlatformToken,
  PatLinkRequest,
  OAuth2LinkRequest,
  OAuth2CallbackRequest,
  OAuth2AuthorizeUrlResponse,
  ConnectionInfo,
} from '../models/user-token.model';
import { ApiResponse } from '../auth/auth.models';
import { environment } from '../../../environments/environment';

describe('UserTokenService', () => {
  let service: UserTokenService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(UserTokenService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  function wrapResponse<T>(data: T): ApiResponse<T> {
    return { success: true, data, message: '', timestamp: new Date().toISOString() };
  }

  function mockToken(overrides: Partial<UserPlatformToken> = {}): UserPlatformToken {
    return {
      id: 'tok-1',
      userId: 'user-1',
      connectionId: 'conn-1',
      tokenType: 'PAT',
      hasRefreshToken: false,
      createdAt: '2026-01-01T00:00:00Z',
      ...overrides,
    };
  }

  function mockConnection(overrides: Partial<ConnectionInfo> = {}): ConnectionInfo {
    return {
      id: 'conn-1',
      tenantId: 'tenant-1',
      name: 'My Jira Cloud',
      platformType: 'JIRA_CLOUD',
      baseUrl: 'https://acme.atlassian.net',
      authType: 'API_TOKEN',
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z',
      ...overrides,
    };
  }

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_linkOAuth2Account_when_calledWithRequest', () => {
    const request: OAuth2LinkRequest = {
      userId: 'user-1',
      connectionId: 'conn-1',
      authorizationCode: 'auth-code-123',
      redirectUri: 'http://localhost:4200/callback',
    };
    const expected = mockToken({ tokenType: 'OAUTH2', hasRefreshToken: true });

    service.linkOAuth2Account(request).subscribe((token) => {
      expect(token.id).toBe('tok-1');
      expect(token.tokenType).toBe('OAUTH2');
      expect(token.hasRefreshToken).toBeTrue();
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/tokens/oauth2/link`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(wrapResponse(expected));
  });

  it('should_linkPatAccount_when_calledWithRequest', () => {
    const request: PatLinkRequest = {
      userId: 'user-1',
      connectionId: 'conn-1',
      accessToken: 'pat-secret-token',
    };
    const expected = mockToken({ tokenType: 'PAT' });

    service.linkPatAccount(request).subscribe((token) => {
      expect(token.tokenType).toBe('PAT');
      expect(token.connectionId).toBe('conn-1');
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/tokens/pat/link`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(wrapResponse(expected));
  });

  it('should_getTokensByUser_when_calledWithUserId', () => {
    const tokens: UserPlatformToken[] = [
      mockToken({ id: 't1', connectionId: 'c1', tokenType: 'PAT' }),
      mockToken({ id: 't2', connectionId: 'c2', tokenType: 'OAUTH2', hasRefreshToken: true }),
    ];

    service.getTokensByUser('user-1').subscribe((result) => {
      expect(result.length).toBe(2);
      expect(result[0].tokenType).toBe('PAT');
      expect(result[1].tokenType).toBe('OAUTH2');
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/tokens/user/user-1`);
    expect(req.request.method).toBe('GET');
    req.flush(wrapResponse(tokens));
  });

  it('should_unlinkAccount_when_calledWithUserIdAndConnectionId', () => {
    service.unlinkAccount('user-1', 'conn-1').subscribe((result) => {
      expect(result).toBeNull();
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/tokens/user/user-1/connection/conn-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should_getAvailableConnections_when_calledWithTenantId', () => {
    const connections: ConnectionInfo[] = [
      mockConnection({ id: 'c1', name: 'Jira Cloud', platformType: 'JIRA_CLOUD' }),
      mockConnection({ id: 'c2', name: 'GitHub', platformType: 'GITHUB' }),
    ];

    service.getAvailableConnections('tenant-1').subscribe((result) => {
      expect(result.length).toBe(2);
      expect(result[0].name).toBe('Jira Cloud');
      expect(result[1].platformType).toBe('GITHUB');
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/tokens/connections/tenant-1`);
    expect(req.request.method).toBe('GET');
    req.flush(wrapResponse(connections));
  });

  it('should_getOAuth2AuthorizeUrl_when_calledWithConnectionId', () => {
    const response: OAuth2AuthorizeUrlResponse = {
      authorizeUrl: 'https://github.com/login/oauth/authorize?client_id=abc&state=xyz',
      state: 'xyz',
    };

    service.getOAuth2AuthorizeUrl('conn-1').subscribe((result) => {
      expect(result.authorizeUrl).toContain('github.com');
      expect(result.state).toBe('xyz');
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/tokens/oauth2/authorize/conn-1`);
    expect(req.request.method).toBe('GET');
    req.flush(wrapResponse(response));
  });

  it('should_handleOAuth2Callback_when_calledWithRequest', () => {
    const request: OAuth2CallbackRequest = {
      userId: 'user-1',
      connectionId: 'conn-1',
      authorizationCode: 'callback-code',
      redirectUri: 'http://localhost:4200/callback',
      state: 'csrf-state',
    };
    const expected = mockToken({ tokenType: 'OAUTH2', hasRefreshToken: true });

    service.handleOAuth2Callback(request).subscribe((token) => {
      expect(token.tokenType).toBe('OAUTH2');
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/tokens/oauth2/callback`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(wrapResponse(expected));
  });

  it('should_unwrapApiResponse_when_getTokensByUserSucceeds', () => {
    service.getTokensByUser('user-1').subscribe((tokens) => {
      expect(tokens).toEqual([]);
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/tokens/user/user-1`);
    req.flush(wrapResponse([]));
  });

  it('should_unwrapApiResponse_when_getAvailableConnectionsSucceeds', () => {
    service.getAvailableConnections('tenant-1').subscribe((connections) => {
      expect(connections).toEqual([]);
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/tokens/connections/tenant-1`);
    req.flush(wrapResponse([]));
  });
});
