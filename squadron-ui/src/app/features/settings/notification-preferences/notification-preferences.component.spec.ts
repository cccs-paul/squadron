import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NotificationPreferencesComponent } from './notification-preferences.component';
import {
  NotificationPreferenceService,
  NotificationPreference,
} from '../../../core/services/notification-preference.service';
import { AuthService } from '../../../core/auth/auth.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

describe('NotificationPreferencesComponent', () => {
  let component: NotificationPreferencesComponent;
  let fixture: ComponentFixture<NotificationPreferencesComponent>;
  let prefServiceSpy: jasmine.SpyObj<NotificationPreferenceService>;
  let authServiceMock: Partial<AuthService>;

  const mockPrefs: NotificationPreference = {
    userId: 'demo-user-001',
    enableEmail: true,
    enableSlack: false,
    enableTeams: false,
    enableInApp: true,
    emailAddress: 'user@acme.com',
    slackWebhookUrl: '',
    teamsWebhookUrl: '',
    mutedEventTypes: ['SYSTEM'],
  };

  beforeEach(async () => {
    prefServiceSpy = jasmine.createSpyObj('NotificationPreferenceService', [
      'getPreferences',
      'updatePreferences',
    ]);

    authServiceMock = {
      user: signal({
        id: 'demo-user-001',
        username: 'testuser',
        email: 'user@acme.com',
        displayName: 'Test User',
        tenantId: 't1',
        tenantName: 'Test Tenant',
        roles: ['squadron-admin'],
        permissions: [],
      }),
      isAuthenticated: signal(true),
      isAdmin: signal(true),
    } as any;

    await TestBed.configureTestingModule({
      imports: [NotificationPreferencesComponent, TranslateModule.forRoot()],
      providers: [
        { provide: NotificationPreferenceService, useValue: prefServiceSpy },
        { provide: AuthService, useValue: authServiceMock },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationPreferencesComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    prefServiceSpy.getPreferences.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should load preferences on init', () => {
    prefServiceSpy.getPreferences.and.returnValue(of(mockPrefs));
    fixture.detectChanges();

    expect(component.enableEmail).toBeTrue();
    expect(component.enableSlack).toBeFalse();
    expect(component.enableInApp).toBeTrue();
    expect(component.emailAddress).toBe('user@acme.com');
    expect(component.loading()).toBeFalse();
  });

  it('should toggle email', () => {
    prefServiceSpy.getPreferences.and.returnValue(of(mockPrefs));
    fixture.detectChanges();

    expect(component.enableEmail).toBeTrue();
    component.enableEmail = false;
    expect(component.enableEmail).toBeFalse();
  });

  it('should toggle slack', () => {
    prefServiceSpy.getPreferences.and.returnValue(of(mockPrefs));
    fixture.detectChanges();

    expect(component.enableSlack).toBeFalse();
    component.enableSlack = true;
    expect(component.enableSlack).toBeTrue();
  });

  it('should save preferences', () => {
    prefServiceSpy.getPreferences.and.returnValue(of(mockPrefs));
    fixture.detectChanges();

    prefServiceSpy.updatePreferences.and.returnValue(of(mockPrefs));
    component.savePreferences();

    expect(prefServiceSpy.updatePreferences).toHaveBeenCalledWith(
      'demo-user-001',
      jasmine.objectContaining({ enableEmail: true }),
    );
    expect(component.saving()).toBeFalse();
    expect(component.saveSuccess()).toBeTrue();
  });

  it('should show webhook URL fields when Slack enabled', () => {
    prefServiceSpy.getPreferences.and.returnValue(of({
      ...mockPrefs,
      enableSlack: true,
      slackWebhookUrl: 'https://hooks.slack.com/test',
    }));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(component.enableSlack).toBeTrue();
    // The slack webhook field should be visible
    const slackInput = el.querySelector('input[placeholder*="hooks.slack.com"]');
    expect(slackInput).toBeTruthy();
  });

  it('should handle save error', () => {
    prefServiceSpy.getPreferences.and.returnValue(of(mockPrefs));
    fixture.detectChanges();

    prefServiceSpy.updatePreferences.and.returnValue(throwError(() => new Error('fail')));
    component.savePreferences();

    expect(component.saving()).toBeFalse();
    // translateService.instant() returns the key when no translations loaded
    expect(component.saveError()).toBe('notifications.saveFailed');
  });

  it('should show muted event types', () => {
    prefServiceSpy.getPreferences.and.returnValue(of(mockPrefs));
    fixture.detectChanges();

    expect(component.mutedEventTypes).toEqual(['SYSTEM']);
    expect(component.isEventMuted('SYSTEM')).toBeTrue();
    expect(component.isEventMuted('TASK_ASSIGNED')).toBeFalse();
  });

  it('should toggle muted event type', () => {
    prefServiceSpy.getPreferences.and.returnValue(of(mockPrefs));
    fixture.detectChanges();

    component.toggleMutedEvent('TASK_ASSIGNED');
    expect(component.isEventMuted('TASK_ASSIGNED')).toBeTrue();

    component.toggleMutedEvent('SYSTEM');
    expect(component.isEventMuted('SYSTEM')).toBeFalse();
  });

  it('should display event type labels and descriptions', () => {
    prefServiceSpy.getPreferences.and.returnValue(of(mockPrefs));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const labels = el.querySelectorAll('.notification-preferences__event-label');
    const descriptions = el.querySelectorAll('.notification-preferences__event-description');

    expect(labels.length).toBe(7);
    expect(descriptions.length).toBe(7);

    // Translate pipe returns the keys when no translations are loaded
    expect(labels[0].textContent?.trim()).toBe('notifications.eventTypes.TASK_ASSIGNED.label');
    expect(descriptions[0].textContent?.trim()).toBe('notifications.eventTypes.TASK_ASSIGNED.description');

    expect(labels[6].textContent?.trim()).toBe('notifications.eventTypes.SYSTEM.label');
    expect(descriptions[6].textContent?.trim()).toBe('notifications.eventTypes.SYSTEM.description');
  });

  it('should have eventTypes with type, labelKey, and descriptionKey', () => {
    prefServiceSpy.getPreferences.and.returnValue(of(mockPrefs));
    fixture.detectChanges();

    expect(component.eventTypes.length).toBe(7);
    for (const evt of component.eventTypes) {
      expect(evt.type).toBeTruthy();
      expect(evt.labelKey).toBeTruthy();
      expect(evt.descriptionKey).toBeTruthy();
    }
    expect(component.eventTypes.map(e => e.type)).toEqual([
      'TASK_ASSIGNED', 'TASK_STATE_CHANGED', 'REVIEW_REQUESTED',
      'REVIEW_COMPLETED', 'AGENT_COMPLETED', 'AGENT_NEEDS_INPUT', 'SYSTEM',
    ]);
  });

  it('should fall back to defaults on load error', () => {
    prefServiceSpy.getPreferences.and.returnValue(throwError(() => new Error('fail')));
    fixture.detectChanges();

    expect(component.enableEmail).toBeFalse();
    expect(component.enableInApp).toBeTrue();
    expect(component.mutedEventTypes).toEqual([]);
    expect(component.loading()).toBeFalse();
  });
});
