import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { PermissionService } from './permission.service';
import { environment } from '../../../environments/environment';

describe('PermissionService', () => {
  let service: PermissionService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(PermissionService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_getPermissions_when_calledWithDefaults', () => {
    service.getPermissions().subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/permissions` &&
      r.params.get('page') === '0' &&
      r.params.get('size') === '50'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 50 });
  });

  it('should_getPermissions_when_calledWithCustomPageAndSize', () => {
    service.getPermissions(2, 100).subscribe();

    const req = httpTesting.expectOne((r) =>
      r.url === `${apiUrl}/permissions` &&
      r.params.get('page') === '2' &&
      r.params.get('size') === '100'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 2, size: 100 });
  });

  it('should_grantPermission_when_calledWithPermissionData', () => {
    const permissionData = { resource: 'TASK', action: 'WRITE', subjectId: 'u1' };
    const mockResponse = { id: 'perm1', ...permissionData };

    service.grantPermission(permissionData as any).subscribe((permission) => {
      expect(permission).toEqual(mockResponse as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/permissions`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(permissionData);
    req.flush(mockResponse);
  });

  it('should_revokePermission_when_calledWithId', () => {
    service.revokePermission('perm1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/permissions/perm1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should_getAuthProviders_when_called', () => {
    const mockProviders = [
      { id: 'ap1', name: 'GitHub OAuth', type: 'OAUTH2' },
      { id: 'ap2', name: 'SAML SSO', type: 'SAML' },
    ];

    service.getAuthProviders().subscribe((providers) => {
      expect(providers).toEqual(mockProviders as any);
      expect(providers.length).toBe(2);
    });

    const req = httpTesting.expectOne(`${apiUrl}/auth-providers`);
    expect(req.request.method).toBe('GET');
    req.flush(mockProviders);
  });

  it('should_getAuthProvider_when_calledWithId', () => {
    const mockProvider = { id: 'ap1', name: 'GitHub OAuth', type: 'OAUTH2' };

    service.getAuthProvider('ap1').subscribe((provider) => {
      expect(provider).toEqual(mockProvider as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/auth-providers/ap1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockProvider);
  });

  it('should_createAuthProvider_when_calledWithProviderData', () => {
    const providerData = { name: 'New Provider', type: 'OAUTH2', clientId: 'abc' };
    const mockResponse = { id: 'ap3', ...providerData };

    service.createAuthProvider(providerData as any).subscribe((provider) => {
      expect(provider).toEqual(mockResponse as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/auth-providers`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(providerData);
    req.flush(mockResponse);
  });

  it('should_updateAuthProvider_when_calledWithIdAndData', () => {
    const updateData = { name: 'Updated Provider' };
    const mockResponse = { id: 'ap1', name: 'Updated Provider' };

    service.updateAuthProvider('ap1', updateData as any).subscribe((provider) => {
      expect(provider).toEqual(mockResponse as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/auth-providers/ap1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(updateData);
    req.flush(mockResponse);
  });

  it('should_deleteAuthProvider_when_calledWithId', () => {
    service.deleteAuthProvider('ap1').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/auth-providers/ap1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should_testAuthProvider_when_calledWithId', () => {
    const mockResult = { success: true, message: 'Provider is reachable' };

    service.testAuthProvider('ap1').subscribe((result) => {
      expect(result).toEqual(mockResult);
    });

    const req = httpTesting.expectOne(`${apiUrl}/auth-providers/ap1/test`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(mockResult);
  });
});
