import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { NotificationPreferenceService, NotificationPreference } from './notification-preference.service';
import { environment } from '../../../environments/environment';

describe('NotificationPreferenceService', () => {
  let service: NotificationPreferenceService;
  let httpTesting: HttpTestingController;
  const apiUrl = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(NotificationPreferenceService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should_beCreated', () => {
    expect(service).toBeTruthy();
  });

  it('should_getPreferences_when_calledWithUserId', () => {
    const mockPrefs: NotificationPreference = {
      userId: 'u1',
      enableEmail: true,
      enableSlack: false,
      enableTeams: false,
      enableInApp: true,
      emailAddress: 'user@acme.com',
      mutedEventTypes: [],
    };

    service.getPreferences('u1').subscribe((result) => {
      expect(result).toEqual(mockPrefs);
    });

    const req = httpTesting.expectOne(`${apiUrl}/notifications/preferences/u1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockPrefs);
  });

  it('should_updatePreferences_when_calledWithUserIdAndPrefs', () => {
    const update: Partial<NotificationPreference> = {
      enableSlack: true,
      slackWebhookUrl: 'https://hooks.slack.com/services/test',
    };

    const mockResponse: NotificationPreference = {
      userId: 'u1',
      enableEmail: true,
      enableSlack: true,
      enableTeams: false,
      enableInApp: true,
      slackWebhookUrl: 'https://hooks.slack.com/services/test',
      mutedEventTypes: [],
    };

    service.updatePreferences('u1', update).subscribe((result) => {
      expect(result.enableSlack).toBeTrue();
      expect(result.slackWebhookUrl).toBe('https://hooks.slack.com/services/test');
    });

    const req = httpTesting.expectOne(`${apiUrl}/notifications/preferences/u1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(update);
    req.flush(mockResponse);
  });

  it('should_sendCorrectUrl_when_calledWithDifferentUserId', () => {
    service.getPreferences('user-abc').subscribe();

    const req = httpTesting.expectOne(`${apiUrl}/notifications/preferences/user-abc`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});
