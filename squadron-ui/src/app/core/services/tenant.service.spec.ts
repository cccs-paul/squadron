import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TenantService } from './tenant.service';
import { environment } from '../../../environments/environment';

describe('TenantService', () => {
  let service: TenantService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(TenantService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_getTenant_when_called', () => {
    const mockTenant = { id: 't1', name: 'Acme Corp', slug: 'acme' };

    service.getTenant().subscribe((tenant) => {
      expect(tenant).toEqual(mockTenant as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/tenants/current`);
    expect(req.request.method).toBe('GET');
    req.flush(mockTenant);
  });

  it('should_sendGetRequest_when_getTenantCalled', () => {
    service.getTenant().subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/tenants/current`);
    expect(req.request.method).toBe('GET');
    expect(req.request.body).toBeNull();
    req.flush({});
  });

  it('should_updateTenantSettings_when_calledWithPartialSettings', () => {
    const settings = { timezone: 'UTC', language: 'en' };
    const mockResponse = { id: 't1', name: 'Acme', settings };

    service.updateTenantSettings(settings as any).subscribe((tenant) => {
      expect(tenant).toEqual(mockResponse as any);
    });

    const req = httpTesting.expectOne(`${apiUrl}/tenants/current/settings`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(settings);
    req.flush(mockResponse);
  });

  it('should_sendPatchMethod_when_updatingSettings', () => {
    service.updateTenantSettings({ timezone: 'PST' } as any).subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/tenants/current/settings`);
    expect(req.request.method).toBe('PATCH');
    req.flush({});
  });

  it('should_sendPartialBody_when_updatingSettingsWithSingleField', () => {
    const partialSettings = { language: 'fr' };

    service.updateTenantSettings(partialSettings as any).subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/tenants/current/settings`);
    expect(req.request.body).toEqual(partialSettings);
    req.flush({});
  });

  it('should_useCorrectBaseUrl_when_makingRequests', () => {
    service.getTenant().subscribe();

    const req = httpTesting.expectOne(`http://localhost:8080/api/tenants/current`);
    expect(req.request.url).toBe('http://localhost:8080/api/tenants/current');
    req.flush({});
  });
});
