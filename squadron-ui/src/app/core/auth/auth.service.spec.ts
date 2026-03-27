import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthUser, LoginRequest, LoginResponse } from './auth.models';
import { environment } from '../../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;
  let router: Router;

  const mockUser: AuthUser = {
    id: 'user-1',
    username: 'jdoe',
    email: 'jdoe@example.com',
    displayName: 'John Doe',
    avatarUrl: 'https://example.com/avatar.png',
    tenantId: 'tenant-1',
    tenantName: 'Acme Corp',
    roles: ['USER'],
    permissions: ['READ', 'WRITE'],
  };

  const mockAdminUser: AuthUser = {
    ...mockUser,
    id: 'admin-1',
    username: 'admin',
    roles: ['USER', 'ADMIN'],
  };

  const mockLoginResponse: LoginResponse = {
    accessToken: 'access-token-123',
    refreshToken: 'refresh-token-456',
    expiresIn: 3600,
    tokenType: 'Bearer',
    user: mockUser,
  };

  const mockAdminLoginResponse: LoginResponse = {
    ...mockLoginResponse,
    user: mockAdminUser,
  };

  beforeEach(() => {
    // Clear localStorage before each test
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });

    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_startUnauthenticated_when_noStoredToken', () => {
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.user()).toBeNull();
    expect(service.isAdmin()).toBeFalse();
  });

  describe('login', () => {
    it('should_loginSuccessfully_when_validCredentials', () => {
      const request: LoginRequest = { username: 'jdoe', password: 'password' };

      service.login(request).subscribe((response) => {
        expect(response).toEqual(mockLoginResponse);
      });

      const req = httpTesting.expectOne(`${environment.apiUrl}/auth/login`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(mockLoginResponse);

      // Verify signals updated
      expect(service.isAuthenticated()).toBeTrue();
      expect(service.user()).toEqual(mockUser);
      expect(service.isAdmin()).toBeFalse();
    });

    it('should_setIsAdminTrue_when_userHasAdminRole', () => {
      const request: LoginRequest = { username: 'admin', password: 'password' };

      service.login(request).subscribe();

      const req = httpTesting.expectOne(`${environment.apiUrl}/auth/login`);
      req.flush(mockAdminLoginResponse);

      expect(service.isAdmin()).toBeTrue();
    });

    it('should_storeTokensInLocalStorage_when_loginSucceeds', () => {
      const request: LoginRequest = { username: 'jdoe', password: 'password' };

      service.login(request).subscribe();

      const req = httpTesting.expectOne(`${environment.apiUrl}/auth/login`);
      req.flush(mockLoginResponse);

      expect(localStorage.getItem('sq_token')).toBe('access-token-123');
      expect(localStorage.getItem('sq_refresh')).toBe('refresh-token-456');
      expect(localStorage.getItem('sq_user')).toBe(JSON.stringify(mockUser));
      expect(localStorage.getItem('sq_expires')).toBeTruthy();
    });

    it('should_clearAuth_when_loginFails', () => {
      // First set some auth state via localStorage
      localStorage.setItem('sq_token', 'old-token');

      const request: LoginRequest = { username: 'jdoe', password: 'wrong' };

      service.login(request).subscribe({
        error: (err) => {
          expect(err.status).toBe(401);
        },
      });

      const req = httpTesting.expectOne(`${environment.apiUrl}/auth/login`);
      req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

      expect(service.isAuthenticated()).toBeFalse();
      expect(service.user()).toBeNull();
      expect(localStorage.getItem('sq_token')).toBeNull();
    });
  });

  describe('handleOidcCallback', () => {
    it('should_authenticateSuccessfully_when_validOidcCallback', () => {
      service.handleOidcCallback('auth-code', 'state-123').subscribe((response) => {
        expect(response).toEqual(mockLoginResponse);
      });

      const req = httpTesting.expectOne(`${environment.apiUrl}/auth/oidc/callback`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ code: 'auth-code', state: 'state-123' });
      req.flush(mockLoginResponse);

      expect(service.isAuthenticated()).toBeTrue();
      expect(service.user()).toEqual(mockUser);
    });
  });

  describe('refreshToken', () => {
    it('should_refreshSuccessfully_when_refreshTokenExists', () => {
      localStorage.setItem('sq_refresh', 'old-refresh-token');

      service.refreshToken().subscribe((response) => {
        expect(response).toEqual(mockLoginResponse);
      });

      const req = httpTesting.expectOne(`${environment.apiUrl}/auth/refresh`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ refreshToken: 'old-refresh-token' });
      req.flush(mockLoginResponse);

      expect(service.isAuthenticated()).toBeTrue();
    });

    it('should_clearAuthAndReturnNull_when_noRefreshToken', () => {
      service.refreshToken().subscribe((response) => {
        expect(response).toBeNull();
      });

      httpTesting.expectNone(`${environment.apiUrl}/auth/refresh`);
      expect(service.isAuthenticated()).toBeFalse();
    });

    it('should_clearAuthAndReturnNull_when_refreshFails', () => {
      localStorage.setItem('sq_refresh', 'expired-token');

      service.refreshToken().subscribe((response) => {
        expect(response).toBeNull();
      });

      const req = httpTesting.expectOne(`${environment.apiUrl}/auth/refresh`);
      req.flush({ message: 'Token expired' }, { status: 401, statusText: 'Unauthorized' });

      expect(service.isAuthenticated()).toBeFalse();
      expect(localStorage.getItem('sq_token')).toBeNull();
    });
  });

  describe('logout', () => {
    it('should_clearAuthAndNavigateToLogin_when_logoutCalled', () => {
      // Set up authenticated state
      localStorage.setItem('sq_token', 'token');
      localStorage.setItem('sq_refresh', 'refresh');

      const navigateSpy = spyOn(router, 'navigate');

      service.logout();

      const req = httpTesting.expectOne(`${environment.apiUrl}/auth/logout`);
      expect(req.request.method).toBe('POST');
      req.flush({});

      expect(service.isAuthenticated()).toBeFalse();
      expect(service.user()).toBeNull();
      expect(localStorage.getItem('sq_token')).toBeNull();
      expect(navigateSpy).toHaveBeenCalledWith(['/login']);
    });

    it('should_stillClearAuthAndNavigate_when_logoutRequestFails', () => {
      localStorage.setItem('sq_token', 'token');
      const navigateSpy = spyOn(router, 'navigate');

      service.logout();

      const req = httpTesting.expectOne(`${environment.apiUrl}/auth/logout`);
      req.flush({}, { status: 500, statusText: 'Server Error' });

      expect(service.isAuthenticated()).toBeFalse();
      expect(navigateSpy).toHaveBeenCalledWith(['/login']);
    });
  });

  describe('getAccessToken', () => {
    it('should_returnToken_when_tokenExistsInStorage', () => {
      localStorage.setItem('sq_token', 'my-access-token');

      expect(service.getAccessToken()).toBe('my-access-token');
    });

    it('should_returnNull_when_noTokenInStorage', () => {
      expect(service.getAccessToken()).toBeNull();
    });
  });

  describe('getAvailableTenants', () => {
    it('should_returnTenantsList_when_called', () => {
      const mockTenants = [
        { id: 't1', name: 'Tenant 1', slug: 'tenant-1' },
        { id: 't2', name: 'Tenant 2', slug: 'tenant-2' },
      ];

      service.getAvailableTenants().subscribe((tenants) => {
        expect(tenants).toEqual(mockTenants);
        expect(tenants.length).toBe(2);
      });

      const req = httpTesting.expectOne(`${environment.apiUrl}/auth/tenants`);
      expect(req.request.method).toBe('GET');
      req.flush(mockTenants);
    });
  });

  describe('token expiration check', () => {
    it('should_clearAuth_when_tokenExistsButExpired', () => {
      localStorage.setItem('sq_token', 'expired-token');
      localStorage.setItem('sq_expires', (Date.now() - 10000).toString());
      localStorage.setItem('sq_user', JSON.stringify(mockUser));

      // Re-create the service to trigger constructor's checkTokenExpiration
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          provideRouter([]),
        ],
      });

      const freshService = TestBed.inject(AuthService);
      httpTesting = TestBed.inject(HttpTestingController);

      expect(freshService.isAuthenticated()).toBeFalse();
      expect(freshService.user()).toBeNull();
      expect(localStorage.getItem('sq_token')).toBeNull();
    });

    it('should_remainAuthenticated_when_tokenNotExpired', () => {
      localStorage.setItem('sq_token', 'valid-token');
      localStorage.setItem('sq_expires', (Date.now() + 3600000).toString());
      localStorage.setItem('sq_user', JSON.stringify(mockUser));

      // Re-create the service via TestBed to test constructor behavior
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          provideRouter([]),
        ],
      });

      const freshService = TestBed.inject(AuthService);
      httpTesting = TestBed.inject(HttpTestingController);

      expect(freshService.isAuthenticated()).toBeTrue();
      expect(freshService.user()).toEqual(mockUser);
    });
  });

  describe('loadStoredUser', () => {
    it('should_returnNull_when_storedUserIsInvalidJson', () => {
      localStorage.setItem('sq_user', 'not-valid-json{{{');

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          provideRouter([]),
        ],
      });

      const freshService = TestBed.inject(AuthService);
      expect(freshService.user()).toBeNull();

      httpTesting = TestBed.inject(HttpTestingController);
    });
  });
});
