import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  NotificationPreferenceService,
  NotificationPreference,
} from '../../../core/services/notification-preference.service';

@Component({
  selector: 'sq-notification-preferences',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './notification-preferences.component.html',
  styleUrl: './notification-preferences.component.scss',
})
export class NotificationPreferencesComponent implements OnInit {
  private prefService = inject(NotificationPreferenceService);

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

  readonly userId = 'demo-user-001';

  readonly eventTypes = [
    'TASK_ASSIGNED',
    'TASK_STATE_CHANGED',
    'REVIEW_REQUESTED',
    'REVIEW_COMPLETED',
    'AGENT_COMPLETED',
    'AGENT_NEEDS_INPUT',
    'SYSTEM',
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
        this.saveError.set('Failed to save preferences. Please try again.');
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
