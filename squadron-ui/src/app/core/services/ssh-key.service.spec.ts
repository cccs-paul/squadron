import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SshKeyService } from './ssh-key.service';
import { SshKey, CreateSshKeyRequest } from '../models/security.model';
import { ApiResponse } from '../auth/auth.models';
import { environment } from '../../../environments/environment';

describe('SshKeyService', () => {
  let service: SshKeyService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(SshKeyService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  function wrapResponse<T>(data: T): ApiResponse<T> {
    return { success: true, data, message: '', timestamp: new Date().toISOString() };
  }

  function mockSshKey(overrides: Partial<SshKey> = {}): SshKey {
    return {
      id: 'key-1',
      tenantId: 'tenant-1',
      connectionId: 'conn-1',
      name: 'My SSH Key',
      publicKey: 'ssh-ed25519 AAAA...',
      fingerprint: 'SHA256:abc123',
      keyType: 'ED25519',
      createdAt: '2026-01-01T00:00:00Z',
      ...overrides,
    };
  }

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_createSshKey_when_calledWithRequest', () => {
    const request: CreateSshKeyRequest = {
      tenantId: 'tenant-1',
      connectionId: 'conn-1',
      name: 'Deploy Key',
      publicKey: 'ssh-ed25519 AAAA...',
      privateKey: '-----BEGIN OPENSSH PRIVATE KEY-----...',
      keyType: 'ED25519',
    };
    const expected = mockSshKey({ name: 'Deploy Key' });

    service.createSshKey(request).subscribe((key) => {
      expect(key.id).toBe('key-1');
      expect(key.name).toBe('Deploy Key');
      expect(key.keyType).toBe('ED25519');
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/ssh-keys`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(wrapResponse(expected));
  });

  it('should_getSshKey_when_calledWithId', () => {
    const expected = mockSshKey();

    service.getSshKey('key-1').subscribe((key) => {
      expect(key.id).toBe('key-1');
      expect(key.name).toBe('My SSH Key');
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/ssh-keys/key-1`);
    expect(req.request.method).toBe('GET');
    req.flush(wrapResponse(expected));
  });

  it('should_getSshKeysByTenant_when_calledWithTenantId', () => {
    const keys: SshKey[] = [
      mockSshKey({ id: 'k1', name: 'Key 1' }),
      mockSshKey({ id: 'k2', name: 'Key 2', keyType: 'RSA' }),
    ];

    service.getSshKeysByTenant('tenant-1').subscribe((result) => {
      expect(result.length).toBe(2);
      expect(result[0].name).toBe('Key 1');
      expect(result[1].keyType).toBe('RSA');
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/ssh-keys/tenant/tenant-1`);
    expect(req.request.method).toBe('GET');
    req.flush(wrapResponse(keys));
  });

  it('should_getSshKeysByConnection_when_calledWithConnectionId', () => {
    const keys: SshKey[] = [mockSshKey()];

    service.getSshKeysByConnection('conn-1').subscribe((result) => {
      expect(result.length).toBe(1);
      expect(result[0].connectionId).toBe('conn-1');
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/ssh-keys/connection/conn-1`);
    expect(req.request.method).toBe('GET');
    req.flush(wrapResponse(keys));
  });

  it('should_deleteSshKey_when_calledWithId', () => {
    service.deleteSshKey('key-1').subscribe((result) => {
      expect(result).toBeNull();
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/ssh-keys/key-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should_unwrapApiResponse_when_getSshKeysByTenantReturnsEmpty', () => {
    service.getSshKeysByTenant('tenant-1').subscribe((keys) => {
      expect(keys).toEqual([]);
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/ssh-keys/tenant/tenant-1`);
    req.flush(wrapResponse([]));
  });

  it('should_unwrapApiResponse_when_getSshKeysByConnectionReturnsEmpty', () => {
    service.getSshKeysByConnection('conn-1').subscribe((keys) => {
      expect(keys).toEqual([]);
    });

    const req = httpTesting.expectOne(`${apiUrl}/platforms/ssh-keys/connection/conn-1`);
    req.flush(wrapResponse([]));
  });
});
