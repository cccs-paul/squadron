import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

export interface NotificationPreference {
  userId: string;
  enableEmail: boolean;
  enableSlack: boolean;
  enableTeams: boolean;
  enableInApp: boolean;
  slackWebhookUrl?: string;
  teamsWebhookUrl?: string;
  emailAddress?: string;
  mutedEventTypes?: string[];
}

@Injectable({ providedIn: 'root' })
export class NotificationPreferenceService extends ApiService {
  getPreferences(userId: string): Observable<NotificationPreference> {
    return this.get<NotificationPreference>(`/notifications/preferences/user/${userId}`);
  }

  updatePreferences(userId: string, prefs: Partial<NotificationPreference>): Observable<NotificationPreference> {
    return this.put<NotificationPreference>(`/notifications/preferences/user/${userId}`, prefs);
  }
}
