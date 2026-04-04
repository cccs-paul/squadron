import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import {
  NotificationPreferenceService,
  NotificationPreference,
} from '../../../core/services/notification-preference.service';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'sq-notification-preferences',
  standalone: true,
  imports: [FormsModule, TranslateModule],
  templateUrl: './notification-preferences.component.html',
  styleUrl: './notification-preferences.component.scss',
})
export class NotificationPreferencesComponent implements OnInit {
  private prefService = inject(NotificationPreferenceService);
  private authService = inject(AuthService);

  loading = signal(true);
  saving = signal(false);
  saveSuccess = signal(false);
  saveError = signal<string | null>(null);

  // Form fields (plain properties for ngModel binding)
  enableEmail = false;
  enableSlack = false;
  enableTeams = false;
  enableInApp = true;
  slackWebhookUrl = '';
  teamsWebhookUrl = '';
  emailAddress = '';
  mutedEventTypes: string[] = [];

  get userId(): string {
    return this.authService.user()?.id ?? '';
  }

  private translateService = inject(TranslateService);

  readonly eventTypes: { type: string; labelKey: string; descriptionKey: string }[] = [
    {
      type: 'TASK_ASSIGNED',
      labelKey: 'notifications.eventTypes.TASK_ASSIGNED.label',
      descriptionKey: 'notifications.eventTypes.TASK_ASSIGNED.description',
    },
    {
      type: 'TASK_STATE_CHANGED',
      labelKey: 'notifications.eventTypes.TASK_STATE_CHANGED.label',
      descriptionKey: 'notifications.eventTypes.TASK_STATE_CHANGED.description',
    },
    {
      type: 'REVIEW_REQUESTED',
      labelKey: 'notifications.eventTypes.REVIEW_REQUESTED.label',
      descriptionKey: 'notifications.eventTypes.REVIEW_REQUESTED.description',
    },
    {
      type: 'REVIEW_COMPLETED',
      labelKey: 'notifications.eventTypes.REVIEW_COMPLETED.label',
      descriptionKey: 'notifications.eventTypes.REVIEW_COMPLETED.description',
    },
    {
      type: 'AGENT_COMPLETED',
      labelKey: 'notifications.eventTypes.AGENT_COMPLETED.label',
      descriptionKey: 'notifications.eventTypes.AGENT_COMPLETED.description',
    },
    {
      type: 'AGENT_NEEDS_INPUT',
      labelKey: 'notifications.eventTypes.AGENT_NEEDS_INPUT.label',
      descriptionKey: 'notifications.eventTypes.AGENT_NEEDS_INPUT.description',
    },
    {
      type: 'SYSTEM',
      labelKey: 'notifications.eventTypes.SYSTEM.label',
      descriptionKey: 'notifications.eventTypes.SYSTEM.description',
    },
  ];

  ngOnInit(): void {
    this.loadPreferences();
  }

  loadPreferences(): void {
    this.loading.set(true);
    this.prefService.getPreferences(this.userId).subscribe({
      next: (prefs) => {
        this.applyPreferences(prefs);
        this.loading.set(false);
      },
      error: () => {
        this.applyDefaults();
        this.loading.set(false);
      },
    });
  }

  savePreferences(): void {
    this.saving.set(true);
    this.saveSuccess.set(false);
    this.saveError.set(null);

    const prefs: Partial<NotificationPreference> = {
      enableEmail: this.enableEmail,
      enableSlack: this.enableSlack,
      enableTeams: this.enableTeams,
      enableInApp: this.enableInApp,
      slackWebhookUrl: this.slackWebhookUrl || undefined,
      teamsWebhookUrl: this.teamsWebhookUrl || undefined,
      emailAddress: this.emailAddress || undefined,
      mutedEventTypes: this.mutedEventTypes,
    };

    this.prefService.updatePreferences(this.userId, prefs).subscribe({
      next: () => {
        this.saving.set(false);
        this.saveSuccess.set(true);
        setTimeout(() => this.saveSuccess.set(false), 3000);
      },
      error: (err) => {
        this.saving.set(false);
        this.saveError.set(this.translateService.instant('notifications.saveFailed'));
        setTimeout(() => this.saveError.set(null), 5000);
      },
    });
  }

  isEventMuted(eventType: string): boolean {
    return this.mutedEventTypes.includes(eventType);
  }

  toggleMutedEvent(eventType: string): void {
    if (this.mutedEventTypes.includes(eventType)) {
      this.mutedEventTypes = this.mutedEventTypes.filter((e) => e !== eventType);
    } else {
      this.mutedEventTypes = [...this.mutedEventTypes, eventType];
    }
  }

  private applyPreferences(prefs: NotificationPreference): void {
    this.enableEmail = prefs.enableEmail;
    this.enableSlack = prefs.enableSlack;
    this.enableTeams = prefs.enableTeams;
    this.enableInApp = prefs.enableInApp;
    this.slackWebhookUrl = prefs.slackWebhookUrl || '';
    this.teamsWebhookUrl = prefs.teamsWebhookUrl || '';
    this.emailAddress = prefs.emailAddress || '';
    this.mutedEventTypes = prefs.mutedEventTypes || [];
  }

  private applyDefaults(): void {
    this.enableEmail = false;
    this.enableSlack = false;
    this.enableTeams = false;
    this.enableInApp = true;
    this.slackWebhookUrl = '';
    this.teamsWebhookUrl = '';
    this.emailAddress = '';
    this.mutedEventTypes = [];
  }
}
