import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, throwError, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ChatRequest } from '../models/chat-request.model';
import { ChatResponse } from '../models/chat-response.model';
import { WebSocketService } from './websocket.service';

/**
 * Service for communicating with the PayBot chat API
 * Uses HTTP POST for sending messages and WebSocket for receiving responses
 * Manages sessionId for server-side history and requestId for idempotency
 */
@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly webSocketService = inject(WebSocketService);
  private readonly apiUrl = `${environment.apiUrl}/chat`;

  private sessionId: string = this.generateSessionId();

  /**
   * Gets the current session ID
   */
  getSessionId(): string {
    return this.sessionId;
  }

  /**
   * Gets the observable stream of WebSocket messages
   * @returns Observable of ChatResponse from WebSocket
   */
  getWebSocketMessages(): Observable<ChatResponse> {
    return this.webSocketService.messages$;
  }

  /**
   * Gets the observable stream of WebSocket connection status
   * @returns Observable of connection status (true = connected)
   */
  getConnectionStatus(): Observable<boolean> {
    return this.webSocketService.connected$;
  }

  /**
   * Connects to the WebSocket server with session-specific topic
   */
  connectWebSocket(): void {
    this.webSocketService.connect(this.sessionId);
  }

  /**
   * Disconnects from the WebSocket server
   */
  disconnectWebSocket(): void {
    this.webSocketService.disconnect();
  }

  /**
   * Checks if WebSocket is connected
   * @returns true if connected
   */
  isWebSocketConnected(): boolean {
    return this.webSocketService.isConnected();
  }

  /**
   * Sends a message to the chat API
   * The API returns 202 Accepted and the actual response comes via WebSocket
   * @param message The user's message
   * @returns Observable that completes when the request is accepted
   */
  sendMessage(message: string): Observable<void> {
    const request: ChatRequest = {
      message,
      requestId: this.generateRequestId(),
      sessionId: this.sessionId
    };

    return this.http
      .post<void>(this.apiUrl, request, { observe: 'response' })
      .pipe(
        catchError(this.handleError),
        // Map the response to void since we only care about acceptance
        // The actual response will come via WebSocket
        catchError(() => of(undefined))
      ) as Observable<void>;
  }

  /**
   * Starts a new conversation by generating a new session ID
   */
  startNewConversation(): void {
    this.sessionId = this.generateSessionId();
    // Reconnect WebSocket with new session topic
    if (this.webSocketService.isConnected()) {
      this.webSocketService.disconnect();
      this.webSocketService.connect(this.sessionId);
    }
  }

  /**
   * Generates a unique session ID
   */
  private generateSessionId(): string {
    return `session_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`;
  }

  /**
   * Generates a unique request ID for idempotency
   */
  private generateRequestId(): string {
    return `req_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`;
  }

  /**
   * Handles HTTP errors from the chat API
   */
  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'An unexpected error occurred. Please try again.';

    if (error.status === 0) {
      // Network error or server unreachable
      errorMessage = 'Unable to connect to the server. Please check your connection.';
    } else if (error.status === 400) {
      errorMessage = 'Invalid request. Please try again.';
    } else if (error.status === 429) {
      errorMessage = 'Too many requests. Please wait a moment and try again.';
    } else if (error.status >= 500) {
      errorMessage = 'Server error. Please try again later.';
    } else if (error.error?.message) {
      errorMessage = error.error.message;
    }

    console.error('Chat API Error:', error);
    return throwError(() => new Error(errorMessage));
  }
}
