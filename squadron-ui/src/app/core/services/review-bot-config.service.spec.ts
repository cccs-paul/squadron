import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ReviewBotConfigService } from './review-bot-config.service';
import { ReviewBotConfig, CreateReviewBotConfigRequest } from '../models/security.model';
import { ApiResponse } from '../auth/auth.models';
import { environment } from '../../../environments/environment';

describe('ReviewBotConfigService', () => {
  let service: ReviewBotConfigService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ReviewBotConfigService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  function wrapResponse<T>(data: T): ApiResponse<T> {
    return { success: true, data, message: '', timestamp: new Date().toISOString() };
  }

  function mockBotConfig(overrides: Partial<ReviewBotConfig> = {}): ReviewBotConfig {
    return {
      id: 'bot-1',
      tenantId: 'tenant-1',
      connectionId: 'conn-1',
      botUsername: 'squadron-bot',
      enabled: true,
      autoAssign: true,
      createdAt: '2026-01-01T00:00:00Z',
      ...overrides,
    };
  }

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_getConfigsByTenant_when_calledWithTenantId', () => {
    const configs: ReviewBotConfig[] = [
      mockBotConfig(),
      mockBotConfig({ id: 'bot-2', connectionId: 'conn-2', botUsername: 'review-bot-2' }),
    ];

    service.getConfigsByTenant('tenant-1').subscribe((result) => {
      expect(result.length).toBe(2);
      expect(result[0].botUsername).toBe('squadron-bot');
      expect(result[1].botUsername).toBe('review-bot-2');
    });

    const req = httpTesting.expectOne(`${apiUrl}/reviews/bot-config/tenant/tenant-1`);
    expect(req.request.method).toBe('GET');
    req.flush(wrapResponse(configs));
  });

  it('should_createConfig_when_calledWithRequest', () => {
    const request: CreateReviewBotConfigRequest = {
      tenantId: 'tenant-1',
      connectionId: 'conn-1',
      botUsername: 'squadron-bot',
      botAccessToken: 'ghp_abc123',
      enabled: true,
      autoAssign: true,
    };
    const expected = mockBotConfig();

    service.createConfig(request).subscribe((config) => {
      expect(config.id).toBe('bot-1');
      expect(config.botUsername).toBe('squadron-bot');
      expect(config.enabled).toBeTrue();
    });

    const req = httpTesting.expectOne(`${apiUrl}/reviews/bot-config`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(wrapResponse(expected));
  });

  it('should_updateConfig_when_calledWithIdAndPartialRequest', () => {
    const update = { enabled: false, autoAssign: false };
    const expected = mockBotConfig({ enabled: false, autoAssign: false });

    service.updateConfig('bot-1', update).subscribe((config) => {
      expect(config.enabled).toBeFalse();
      expect(config.autoAssign).toBeFalse();
    });

    const req = httpTesting.expectOne(`${apiUrl}/reviews/bot-config/bot-1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(update);
    req.flush(wrapResponse(expected));
  });

  it('should_deleteConfig_when_calledWithId', () => {
    service.deleteConfig('bot-1').subscribe((result) => {
      expect(result).toBeNull();
    });

    const req = httpTesting.expectOne(`${apiUrl}/reviews/bot-config/bot-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should_returnEmptyArray_when_noConfigsExist', () => {
    service.getConfigsByTenant('tenant-1').subscribe((configs) => {
      expect(configs).toEqual([]);
    });

    const req = httpTesting.expectOne(`${apiUrl}/reviews/bot-config/tenant/tenant-1`);
    req.flush(wrapResponse([]));
  });

  it('should_unwrapApiResponse_correctly', () => {
    const config = mockBotConfig();

    service.getConfigsByTenant('tenant-1').subscribe((result) => {
      expect(result.length).toBe(1);
      expect(result[0].connectionId).toBe('conn-1');
    });

    const req = httpTesting.expectOne(`${apiUrl}/reviews/bot-config/tenant/tenant-1`);
    req.flush(wrapResponse([config]));
  });

  it('should_updateConfig_withPartialFields', () => {
    const update = { botUsername: 'new-bot-name' };
    const expected = mockBotConfig({ botUsername: 'new-bot-name' });

    service.updateConfig('bot-1', update).subscribe((config) => {
      expect(config.botUsername).toBe('new-bot-name');
    });

    const req = httpTesting.expectOne(`${apiUrl}/reviews/bot-config/bot-1`);
    expect(req.request.method).toBe('PUT');
    req.flush(wrapResponse(expected));
  });
});
