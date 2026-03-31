import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient, HttpErrorResponse } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { environment } from '../../../environments/environment';
import { of, throwError } from 'rxjs';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpTesting: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();

    authServiceSpy = jasmine.createSpyObj('AuthService', ['getAccessToken', 'logout', 'refreshToken']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('should_addAuthorizationHeader_when_tokenExists', () => {
    authServiceSpy.getAccessToken.and.returnValue('my-token-123');

    httpClient.get(`${environment.apiUrl}/tasks`).subscribe();

    const req = httpTesting.expectOne(`${environment.apiUrl}/tasks`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer my-token-123');
    req.flush({});
  });

  it('should_notAddAuthorizationHeader_when_noTokenExists', () => {
    authServiceSpy.getAccessToken.and.returnValue(null);

    httpClient.get(`${environment.apiUrl}/tasks`).subscribe();

    const req = httpTesting.expectOne(`${environment.apiUrl}/tasks`);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('should_notAddAuthorizationHeader_when_urlIsAuthLogin', () => {
    authServiceSpy.getAccessToken.and.returnValue('my-token-123');

    httpClient.post(`${environment.apiUrl}/auth/login`, {}).subscribe();

    const req = httpTesting.expectOne(`${environment.apiUrl}/auth/login`);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('should_notAddAuthorizationHeader_when_urlIsAuthTenants', () => {
    authServiceSpy.getAccessToken.and.returnValue('my-token-123');

    httpClient.get(`${environment.apiUrl}/auth/tenants`).subscribe();

    const req = httpTesting.expectOne(`${environment.apiUrl}/auth/tenants`);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush([]);
  });

  it('should_addAuthorizationHeader_when_urlIsAuthRefresh', () => {
    authServiceSpy.getAccessToken.and.returnValue('my-token-123');

    httpClient.post(`${environment.apiUrl}/auth/refresh`, {}).subscribe();

    const req = httpTesting.expectOne(`${environment.apiUrl}/auth/refresh`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer my-token-123');
    req.flush({});
  });

  it('should_attemptTokenRefresh_when_401Error', () => {
    authServiceSpy.getAccessToken.and.returnValue('expired-token');
    const refreshResponse = { accessToken: 'new-token', refreshToken: 'new-refresh', expiresIn: 3600, tokenType: 'Bearer', user: {} };
    authServiceSpy.refreshToken.and.returnValue(of(refreshResponse as any));

    httpClient.get(`${environment.apiUrl}/tasks`).subscribe();

    const req = httpTesting.expectOne(`${environment.apiUrl}/tasks`);
    req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.refreshToken).toHaveBeenCalled();
    expect(authServiceSpy.logout).not.toHaveBeenCalled();

    // The retry request should use the new token
    const retryReq = httpTesting.expectOne(`${environment.apiUrl}/tasks`);
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer new-token');
    retryReq.flush({});
  });

  it('should_callLogoutAndNavigateToLogin_when_refreshFails', () => {
    authServiceSpy.getAccessToken.and.returnValue('expired-token');
    authServiceSpy.refreshToken.and.returnValue(throwError(() => new Error('refresh failed')));
    const navigateSpy = spyOn(router, 'navigate');

    httpClient.get(`${environment.apiUrl}/tasks`).subscribe({
      error: (err: HttpErrorResponse) => {
        expect(err.status).toBe(401);
      },
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/tasks`);
    req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.refreshToken).toHaveBeenCalled();
    expect(authServiceSpy.logout).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('should_callLogoutAndNavigateToLogin_when_refreshReturnsNull', () => {
    authServiceSpy.getAccessToken.and.returnValue('expired-token');
    authServiceSpy.refreshToken.and.returnValue(of(null));
    const navigateSpy = spyOn(router, 'navigate');

    httpClient.get(`${environment.apiUrl}/tasks`).subscribe({
      error: (err: HttpErrorResponse) => {
        expect(err.status).toBe(401);
      },
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/tasks`);
    req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.refreshToken).toHaveBeenCalled();
    expect(authServiceSpy.logout).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('should_notAttemptRefresh_when_401OnRefreshEndpoint', () => {
    authServiceSpy.getAccessToken.and.returnValue('expired-token');

    httpClient.post(`${environment.apiUrl}/auth/refresh`, {}).subscribe({
      error: (err: HttpErrorResponse) => {
        expect(err.status).toBe(401);
      },
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/auth/refresh`);
    req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();
    expect(authServiceSpy.logout).not.toHaveBeenCalled();
  });

  it('should_notCallLogout_when_nonUnauthorizedError', () => {
    authServiceSpy.getAccessToken.and.returnValue('valid-token');

    httpClient.get(`${environment.apiUrl}/tasks`).subscribe({
      error: (err: HttpErrorResponse) => {
        expect(err.status).toBe(500);
      },
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/tasks`);
    req.flush({ message: 'Server Error' }, { status: 500, statusText: 'Server Error' });

    expect(authServiceSpy.logout).not.toHaveBeenCalled();
  });

  it('should_passRequestThrough_when_successfulResponse', () => {
    authServiceSpy.getAccessToken.and.returnValue('valid-token');
    const mockData = { id: '1', name: 'Task 1' };

    httpClient.get(`${environment.apiUrl}/tasks/1`).subscribe((data) => {
      expect(data).toEqual(mockData);
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/tasks/1`);
    req.flush(mockData);

    expect(authServiceSpy.logout).not.toHaveBeenCalled();
  });

  it('should_rethrowError_when_errorOccurs', () => {
    authServiceSpy.getAccessToken.and.returnValue('valid-token');
    let errorReceived = false;

    httpClient.get(`${environment.apiUrl}/tasks`).subscribe({
      error: (err: HttpErrorResponse) => {
        errorReceived = true;
        expect(err.status).toBe(403);
      },
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/tasks`);
    req.flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

    expect(errorReceived).toBeTrue();
  });
});
