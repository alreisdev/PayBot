import { Component, inject } from '@angular/core';
import { ChatService } from '../../core/services/chat.service';
import { MessageStoreService } from '../../core/services/message-store.service';
import { MessageListComponent } from './message-list/message-list.component';
import { ChatInputComponent } from './chat-input/chat-input.component';
import { TypingIndicatorComponent } from './typing-indicator/typing-indicator.component';

/**
 * Main chat page component that orchestrates the chat experience
 */
@Component({
  selector: 'app-chat-page',
  standalone: true,
  imports: [MessageListComponent, ChatInputComponent, TypingIndicatorComponent],
  templateUrl: './chat-page.component.html',
  styleUrl: './chat-page.component.scss'
})
export class ChatPageComponent {
  private readonly chatService = inject(ChatService);
  protected readonly messageStore = inject(MessageStoreService);

  /** Suggested prompts for empty state */
  protected readonly suggestions = [
    'How do I check my account balance?',
    'Help me transfer money to a friend',
    'What are the payment options available?',
    'Show me my recent transactions'
  ];

  /**
   * Handles sending a message to the chat API
   * @param content The message content to send
   */
  onSendMessage(content: string): void {
    if (!content.trim()) return;

    // Add user message to store
    this.messageStore.addUserMessage(content);

    // Get conversation history for context
    const history = this.messageStore.getConversationHistory();
    // Remove the last message (current user message) from history
    const previousHistory = history.slice(0, -1);

    // Set loading state
    this.messageStore.setLoading(true);

    // Send message to API
    this.chatService.sendMessage(content, previousHistory).subscribe({
      next: (response) => {
        this.messageStore.addAssistantMessage(response.message.content);
        this.messageStore.setLoading(false);
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
   */
  onNewConversation(): void {
    this.messageStore.clearMessages();
  }
}
