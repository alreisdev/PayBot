import { Injectable, computed, signal } from '@angular/core';
import { Message, createMessage } from '../models/message.model';
import { ConversationMessage } from '../models/chat-request.model';

/**
 * State management service for chat messages using Angular Signals
 */
@Injectable({
  providedIn: 'root'
})
export class MessageStoreService {
  // Private writable signals
  private readonly _messages = signal<Message[]>([]);
  private readonly _isLoading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);

  // Public readonly signals
  /** All messages in the conversation */
  readonly messages = this._messages.asReadonly();

  /** Whether the assistant is currently generating a response */
  readonly isLoading = this._isLoading.asReadonly();

  /** Current error message, if any */
  readonly error = this._error.asReadonly();

  /** Whether the conversation has any messages */
  readonly hasMessages = computed(() => this._messages().length > 0);

  /** The most recent message in the conversation */
  readonly lastMessage = computed(() => {
    const msgs = this._messages();
    return msgs.length > 0 ? msgs[msgs.length - 1] : null;
  });

  /** Number of messages in the conversation */
  readonly messageCount = computed(() => this._messages().length);

  /**
   * Gets the conversation history in the format expected by the API
   */
  getConversationHistory(): ConversationMessage[] {
    return this._messages().map((msg) => ({
      role: msg.role,
      content: msg.content
    }));
  }

  /**
   * Adds a user message to the store
   * @param content The message content
   * @returns The created message
   */
  addUserMessage(content: string): Message {
    const message = createMessage('user', content);
    this._messages.update((messages) => [...messages, message]);
    this.clearError();
    return message;
  }

  /**
   * Adds an assistant message to the store
   * @param content The message content
   * @returns The created message
   */
  addAssistantMessage(content: string): Message {
    const message = createMessage('assistant', content);
    this._messages.update((messages) => [...messages, message]);
    return message;
  }

  /**
   * Sets the loading state
   * @param loading Whether the assistant is generating a response
   */
  setLoading(loading: boolean): void {
    this._isLoading.set(loading);
  }

  /**
   * Sets an error message
   * @param error The error message
   */
  setError(error: string): void {
    this._error.set(error);
  }

  /**
   * Clears the current error
   */
  clearError(): void {
    this._error.set(null);
  }

  /**
   * Clears all messages and resets the conversation
   */
  clearMessages(): void {
    this._messages.set([]);
    this._isLoading.set(false);
    this._error.set(null);
  }

  /**
   * Removes a specific message by ID
   * @param messageId The ID of the message to remove
   */
  removeMessage(messageId: string): void {
    this._messages.update((messages) =>
      messages.filter((msg) => msg.id !== messageId)
    );
  }
}
