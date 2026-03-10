import { Injectable, NgZone, inject, OnDestroy } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Subject, Observable } from 'rxjs';
import { ChatResponse } from '../models/chat-response.model';
import { environment } from '../../../environments/environment';

/**
 * WebSocket service for real-time communication with the PayBot backend
 * Uses STOMP over SockJS for compatibility
 */
@Injectable({
  providedIn: 'root'
})
export class WebSocketService implements OnDestroy {
  private readonly ngZone = inject(NgZone);

  private client: Client | null = null;
  private subscription: StompSubscription | null = null;
  private messageSubject = new Subject<ChatResponse>();
  private connectionSubject = new Subject<boolean>();
  private reconnectAttempts = 0;
  private readonly maxReconnectAttempts = 5;
  private readonly reconnectDelay = 3000;
  private reconnectTimeout: ReturnType<typeof setTimeout> | null = null;

  /** Observable stream of incoming chat messages */
  public readonly messages$: Observable<ChatResponse> = this.messageSubject.asObservable();

  /** Observable stream of connection status changes */
  public readonly connected$: Observable<boolean> = this.connectionSubject.asObservable();

  /**
   * Constructs the WebSocket URL
   * In development: http://localhost:8080/api -> http://localhost:8080/ws-paybot
   * In production: /api -> http(s)://current-host/ws-paybot
   */
  private getWebSocketUrl(): string {
    if (environment.production) {
      // In production, use the current host (nginx proxies to backend)
      const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
      return `${protocol}//${window.location.host}/ws-paybot`;
    } else {
      // In development, extract from API URL
      const baseUrl = environment.apiUrl.replace('/api', '');
      return `${baseUrl}/ws-paybot`;
    }
  }

  /**
   * Establishes WebSocket connection to the backend
   */
  connect(): void {
    if (this.client?.connected) {
      console.log('WebSocket already connected');
      return;
    }

    const wsUrl = this.getWebSocketUrl();
    console.log('Connecting to WebSocket at:', wsUrl);

    this.client = new Client({
      // Use SockJS for fallback transport
      webSocketFactory: () => new SockJS(wsUrl) as WebSocket,

      // Heartbeat configuration
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      // Reconnect configuration - we handle this manually
      reconnectDelay: 0, // Disable auto-reconnect, we'll handle it ourselves

      // Connection callbacks
      onConnect: () => {
        this.ngZone.run(() => {
          console.log('WebSocket connected successfully');
          this.reconnectAttempts = 0;
          this.connectionSubject.next(true);
          this.subscribeToMessages();
        });
      },

      onDisconnect: () => {
        this.ngZone.run(() => {
          console.log('WebSocket disconnected');
          this.connectionSubject.next(false);
        });
      },

      onStompError: (frame) => {
        this.ngZone.run(() => {
          console.error('STOMP error:', frame.headers['message']);
          console.error('Details:', frame.body);
          this.connectionSubject.next(false);
          this.attemptReconnect();
        });
      },

      onWebSocketClose: () => {
        this.ngZone.run(() => {
          console.log('WebSocket connection closed');
          this.connectionSubject.next(false);
          this.attemptReconnect();
        });
      },

      onWebSocketError: (error) => {
        this.ngZone.run(() => {
          console.error('WebSocket error:', error);
          this.connectionSubject.next(false);
        });
      }
    });

    this.client.activate();
  }

  /**
   * Subscribes to the messages topic
   */
  private subscribeToMessages(): void {
    if (!this.client?.connected) {
      console.warn('Cannot subscribe: WebSocket not connected');
      return;
    }

    // Unsubscribe from previous subscription if exists
    if (this.subscription) {
      this.subscription.unsubscribe();
    }

    this.subscription = this.client.subscribe('/topic/messages', (message: IMessage) => {
      this.ngZone.run(() => {
        try {
          const chatResponse: ChatResponse = JSON.parse(message.body);
          console.log('Received WebSocket message:', chatResponse);
          this.messageSubject.next(chatResponse);
        } catch (error) {
          console.error('Error parsing WebSocket message:', error);
        }
      });
    });

    console.log('Subscribed to /topic/messages');
  }

  /**
   * Attempts to reconnect after a connection failure
   */
  private attemptReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('Max reconnection attempts reached');
      return;
    }

    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
    }

    this.reconnectAttempts++;
    console.log(`Attempting to reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);

    this.reconnectTimeout = setTimeout(() => {
      this.connect();
    }, this.reconnectDelay);
  }

  /**
   * Disconnects from the WebSocket server
   */
  disconnect(): void {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }

    if (this.subscription) {
      this.subscription.unsubscribe();
      this.subscription = null;
    }

    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }

    this.connectionSubject.next(false);
    console.log('WebSocket disconnected');
  }

  /**
   * Checks if the WebSocket is currently connected
   * @returns true if connected, false otherwise
   */
  isConnected(): boolean {
    return this.client?.connected ?? false;
  }

  /**
   * Resets reconnection attempts counter
   * Call this when user manually triggers a reconnect
   */
  resetReconnectAttempts(): void {
    this.reconnectAttempts = 0;
  }

  /**
   * Cleanup on service destruction
   */
  ngOnDestroy(): void {
    this.disconnect();
    this.messageSubject.complete();
    this.connectionSubject.complete();
  }
}
