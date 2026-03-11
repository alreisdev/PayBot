import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { ChatService } from '../../core/services/chat.service';
import { MessageStoreService } from '../../core/services/message-store.service';
import { MessageListComponent } from './message-list/message-list.component';
import { ChatInputComponent } from './chat-input/chat-input.component';
import { TypingIndicatorComponent } from './typing-indicator/typing-indicator.component';

/**
 * Main chat page component that orchestrates the chat experience
 * Uses WebSocket for real-time message reception
 */
@Component({
  selector: 'app-chat-page',
  standalone: true,
  imports: [MessageListComponent, ChatInputComponent, TypingIndicatorComponent],
  templateUrl: './chat-page.component.html',
  styleUrl: './chat-page.component.scss'
})
export class ChatPageComponent implements OnInit, OnDestroy {
  private readonly chatService = inject(ChatService);
  protected readonly messageStore = inject(MessageStoreService);

  private webSocketSubscription: Subscription | null = null;
  private connectionSubscription: Subscription | null = null;

  /** Whether the WebSocket is connected */
  protected readonly isConnected = signal<boolean>(false);

  /** Suggested prompts for empty state */
  protected readonly suggestions = [
    'How do I check my account balance?',
    'Help me transfer money to a friend',
    'What are the payment options available?',
    'Show me my recent transactions'
  ];

  ngOnInit(): void {
    // Connect to WebSocket on component initialization
    this.chatService.connectWebSocket();

    // Subscribe to WebSocket messages
    this.webSocketSubscription = this.chatService.getWebSocketMessages().subscribe({
      next: (response) => {
        // Add assistant message from WebSocket
        this.messageStore.addAssistantMessage(response.message.content);
        // Stop loading indicator
        this.messageStore.setLoading(false);
      },
      error: (error) => {
        console.error('WebSocket message error:', error);
        this.messageStore.setError('Failed to receive response. Please try again.');
        this.messageStore.setLoading(false);
      }
    });

    // Subscribe to connection status changes
    this.connectionSubscription = this.chatService.getConnectionStatus().subscribe({
      next: (connected) => {
        this.isConnected.set(connected);
        if (!connected && this.messageStore.isLoading()) {
          // If disconnected while waiting for response, show error
          this.messageStore.setError('Connection lost. Please try again.');
          this.messageStore.setLoading(false);
        }
      }
    });
  }

  ngOnDestroy(): void {
    // Clean up subscriptions
    this.webSocketSubscription?.unsubscribe();
    this.connectionSubscription?.unsubscribe();

    // Disconnect WebSocket
    this.chatService.disconnectWebSocket();
  }

  /**
   * Handles sending a message to the chat API
   * @param content The message content to send
   */
  onSendMessage(content: string): void {
    if (!content.trim()) return;

    // Add user message to store (for UI display only)
    this.messageStore.addUserMessage(content);

    // Set loading state (typing indicator)
    this.messageStore.setLoading(true);

    // Send message to API (returns 202 Accepted)
    // History is managed server-side via Redis
    // The actual response will come via WebSocket
    this.chatService.sendMessage(content).subscribe({
      next: () => {
        // Request accepted - waiting for WebSocket response
        // Loading state remains true until WebSocket message arrives
        console.log('Message sent, waiting for WebSocket response...');
      },
      error: (error: Error) => {
        this.messageStore.setError(error.message);
        this.messageStore.setLoading(false);
      }
    });
  }

  /**
   * Handles clicking a suggestion in the empty state
   * @param suggestion The suggestion text
   */
  onSuggestionClick(suggestion: string): void {
    this.onSendMessage(suggestion);
  }

  /**
   * Clears all messages and starts a new conversation
   * Also generates a new session ID for server-side history
   */
  onNewConversation(): void {
    this.messageStore.clearMessages();
    this.chatService.startNewConversation();
  }
}
