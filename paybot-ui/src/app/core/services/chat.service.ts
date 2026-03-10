import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, throwError, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ChatRequest, ConversationMessage } from '../models/chat-request.model';
import { ChatResponse } from '../models/chat-response.model';
import { WebSocketService } from './websocket.service';

/**
 * Service for communicating with the PayBot chat API
 * Uses HTTP POST for sending messages and WebSocket for receiving responses
 */
@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly webSocketService = inject(WebSocketService);
  private readonly apiUrl = `${environment.apiUrl}/chat`;

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
   * Connects to the WebSocket server
   */
  connectWebSocket(): void {
    this.webSocketService.connect();
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
   * @param conversationHistory Previous messages for context
   * @returns Observable that completes when the request is accepted
   */
  sendMessage(
    message: string,
    conversationHistory: ConversationMessage[] = []
  ): Observable<void> {
    const request: ChatRequest = {
      message,
      conversationHistory
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
