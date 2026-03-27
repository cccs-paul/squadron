import { Injectable, OnDestroy, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Observable, Subject } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'error';

/**
 * WebSocketService manages a single STOMP connection over native WebSocket
 * to the backend agent service at /ws/agent.
 */
@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private client: Client | null = null;
  private subscriptions = new Map<string, StompSubscription>();

  /** Reactive connection state exposed as a signal. */
  readonly connectionState = signal<ConnectionState>('disconnected');

  connect(): void {
    if (this.client?.connected) {
      return;
    }

    this.connectionState.set('connecting');

    // Build the WebSocket URL from environment wsUrl.
    // The backend endpoint is /ws/agent — SockJS adds /websocket for native WS.
    const wsUrl = this.buildWsUrl();

    this.client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.connectionState.set('connected');
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message'], frame.body);
        this.connectionState.set('error');
      },
      onDisconnect: () => {
        this.connectionState.set('disconnected');
      },
      onWebSocketClose: () => {
        if (this.connectionState() !== 'disconnected') {
          this.connectionState.set('disconnected');
        }
      },
    });

    this.client.activate();
  }

  disconnect(): void {
    if (this.client) {
      this.subscriptions.forEach((sub) => sub.unsubscribe());
      this.subscriptions.clear();
      this.client.deactivate();
      this.client = null;
      this.connectionState.set('disconnected');
    }
  }

  /**
   * Subscribe to a STOMP topic and return an Observable of parsed messages.
   * Automatically connects if not already connected.
   */
  subscribe<T>(destination: string): Observable<T> {
    const subject = new Subject<T>();

    const doSubscribe = () => {
      if (!this.client) {
        return;
      }
      const sub = this.client.subscribe(destination, (message: IMessage) => {
        try {
          const body = JSON.parse(message.body) as T;
          subject.next(body);
        } catch (e) {
          console.error('Failed to parse STOMP message:', e);
        }
      });
      this.subscriptions.set(destination, sub);
    };

    if (this.client?.connected) {
      doSubscribe();
    } else {
      this.connect();
      // Wait for connection before subscribing
      const checkInterval = setInterval(() => {
        if (this.client?.connected) {
          clearInterval(checkInterval);
          doSubscribe();
        }
      }, 100);
      // Timeout after 10 seconds
      setTimeout(() => clearInterval(checkInterval), 10000);
    }

    return subject.asObservable();
  }

  /**
   * Unsubscribe from a specific STOMP destination.
   */
  unsubscribe(destination: string): void {
    const sub = this.subscriptions.get(destination);
    if (sub) {
      sub.unsubscribe();
      this.subscriptions.delete(destination);
    }
  }

  /**
   * Publish a message to a STOMP destination.
   * The body is JSON-serialized automatically.
   */
  publish(destination: string, body: unknown): void {
    if (!this.client?.connected) {
      console.error('Cannot publish: STOMP client not connected');
      return;
    }
    this.client.publish({
      destination,
      body: JSON.stringify(body),
    });
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  private buildWsUrl(): string {
    const base = environment.wsUrl;
    // Convert ws:// or wss:// URL to full endpoint
    // Backend registers /ws/agent with SockJS — for native WebSocket, use /ws/agent/websocket
    if (base.startsWith('ws://') || base.startsWith('wss://')) {
      return `${base}/agent/websocket`;
    }
    // Relative URL — build from window.location
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}${base}/agent/websocket`;
  }
}
