import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ChatRequest, ConversationMessage } from '../models/chat-request.model';
import { ChatResponse } from '../models/chat-response.model';

/**
 * Service for communicating with the PayBot chat API
 */
@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/chat`;

  /**
   * Sends a message to the chat API and returns the assistant's response
   * @param message The user's message
   * @param conversationHistory Previous messages for context
   * @returns Observable of ChatResponse
   */
  sendMessage(
    message: string,
    conversationHistory: ConversationMessage[] = []
  ): Observable<ChatResponse> {
    const request: ChatRequest = {
      message,
      conversationHistory
    };

    return this.http
      .post<ChatResponse>(this.apiUrl, request)
      .pipe(catchError(this.handleError));
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
