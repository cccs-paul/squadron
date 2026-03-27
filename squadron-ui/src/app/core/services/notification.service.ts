import { Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ApiService, PageResponse } from './api.service';
import { Notification } from '../models/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationService extends ApiService {
  readonly unreadCount = signal(0);
  readonly notifications = signal<Notification[]>([]);

  getNotifications(page = 0, size = 20): Observable<PageResponse<Notification>> {
    return this.get<PageResponse<Notification>>('/notifications', { page, size }).pipe(
      tap((response) => {
        this.notifications.set(response.content);
        this.unreadCount.set(response.content.filter((n) => !n.read).length);
      }),
    );
  }

  markAsRead(id: string): Observable<void> {
    return this.post<void>(`/notifications/${id}/read`, {}).pipe(
      tap(() => {
        this.notifications.update((notifs) =>
          notifs.map((n) => (n.id === id ? { ...n, read: true } : n)),
        );
        this.unreadCount.update((count) => Math.max(0, count - 1));
      }),
    );
  }

  markAllAsRead(): Observable<void> {
    return this.post<void>('/notifications/read-all', {}).pipe(
      tap(() => {
        this.notifications.update((notifs) => notifs.map((n) => ({ ...n, read: true })));
        this.unreadCount.set(0);
      }),
    );
  }
}
