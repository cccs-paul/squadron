import { Injectable, inject, OnDestroy, signal } from '@angular/core';
import { Observable, Subject, tap } from 'rxjs';
import { Client, IMessage } from '@stomp/stompjs';
import { ApiService, PageResponse } from './api.service';
import { AuthService } from '../auth/auth.service';
import { Notification } from '../models/notification.model';
import { environment } from '../../../environments/environment';

/** A toast notification displayed temporarily in the UI. */
export interface ToastNotification {
  id: string;
  title: string;
  message: string;
  type: 'info' | 'success' | 'warning' | 'error';
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService extends ApiService implements OnDestroy {
  private authService = inject(AuthService);

  readonly unreadCount = signal(0);
  readonly notifications = signal<Notification[]>([]);
  readonly toasts = signal<ToastNotification[]>([]);

  private wsClient: Client | null = null;
  private wsConnected = false;
  private readonly incomingNotification$ = new Subject<Notification>();

  /** Observable that emits when a new notification arrives via WebSocket. */
  readonly onNotification$ = this.incomingNotification$.asObservable();

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

  /**
   * Connect to the notification WebSocket endpoint and subscribe to
   * /topic/notifications/{userId} for real-time push notifications.
   */
  connectWebSocket(userId: string): void {
    if (this.wsConnected || this.wsClient?.connected) {
      return;
    }

    const wsUrl = this.buildNotificationWsUrl();

    this.wsClient = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.wsConnected = true;
        this.wsClient!.subscribe(`/topic/notifications/${userId}`, (message: IMessage) => {
          this.handleIncomingNotification(message);
        });
      },
      onStompError: (frame) => {
        console.error('Notification WebSocket STOMP error:', frame.headers['message']);
      },
      onDisconnect: () => {
        this.wsConnected = false;
      },
      onWebSocketClose: () => {
        this.wsConnected = false;
      },
    });

    this.wsClient.activate();
  }

  disconnectWebSocket(): void {
    if (this.wsClient) {
      this.wsClient.deactivate();
      this.wsClient = null;
      this.wsConnected = false;
    }
  }

  /** Dismiss a toast by id. */
  dismissToast(id: string): void {
    this.toasts.update((t) => t.filter((toast) => toast.id !== id));
  }

  ngOnDestroy(): void {
    this.disconnectWebSocket();
  }

  /**
   * Map the raw backend entity payload to the frontend Notification interface.
   * Backend fields: subject, body, status, eventType, readAt, createdAt
   * Frontend fields: title, message, type, read, createdAt
   */
  mapBackendNotification(raw: Record<string, unknown>): Notification {
    return {
      id: String(raw['id'] ?? ''),
      tenantId: String(raw['tenantId'] ?? ''),
      userId: String(raw['userId'] ?? ''),
      type: (raw['eventType'] as string ?? 'SYSTEM') as Notification['type'],
      title: String(raw['subject'] ?? raw['title'] ?? 'New Notification'),
      message: String(raw['body'] ?? raw['message'] ?? ''),
      link: raw['link'] as string | undefined,
      read: raw['readAt'] != null || raw['read'] === true,
      createdAt: String(raw['createdAt'] ?? new Date().toISOString()),
    };
  }

  private handleIncomingNotification(message: IMessage): void {
    try {
      const raw = JSON.parse(message.body) as Record<string, unknown>;
      const notification = this.mapBackendNotification(raw);

      // Prepend to notifications list
      this.notifications.update((notifs) => [notification, ...notifs]);
      this.unreadCount.update((count) => count + 1);

      // Emit for subscribers
      this.incomingNotification$.next(notification);

      // Add toast
      const toast: ToastNotification = {
        id: notification.id || crypto.randomUUID(),
        title: notification.title,
        message: notification.message,
        type: this.toastTypeFromNotificationType(notification.type),
        createdAt: new Date().toISOString(),
      };
      this.toasts.update((t) => [...t, toast]);

      // Auto-dismiss toast after 5 seconds
      setTimeout(() => this.dismissToast(toast.id), 5000);
    } catch (e) {
      console.error('Failed to parse notification WebSocket message:', e);
    }
  }

  private toastTypeFromNotificationType(type: string): ToastNotification['type'] {
    switch (type) {
      case 'AGENT_COMPLETED':
      case 'REVIEW_COMPLETED':
        return 'success';
      case 'AGENT_NEEDS_INPUT':
        return 'warning';
      case 'SYSTEM':
        return 'info';
      default:
        return 'info';
    }
  }

  private buildNotificationWsUrl(): string {
    const base = environment.wsUrl;
    const token = this.authService.getAccessToken();
    const tokenParam = token ? `?access_token=${encodeURIComponent(token)}` : '';
    if (base.startsWith('ws://') || base.startsWith('wss://')) {
      return `${base}/notifications/websocket${tokenParam}`;
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}${base}/notifications/websocket${tokenParam}`;
  }
}
