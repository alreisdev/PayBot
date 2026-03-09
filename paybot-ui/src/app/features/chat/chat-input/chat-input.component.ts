import {
  Component,
  ElementRef,
  ViewChild,
  output,
  input,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';

/**
 * Chat input component with auto-expanding textarea
 * Supports Enter to send and Shift+Enter for newline
 */
@Component({
  selector: 'app-chat-input',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './chat-input.component.html',
  styleUrl: './chat-input.component.scss'
})
export class ChatInputComponent {
  /** Whether the input is disabled (e.g., while loading) */
  disabled = input<boolean>(false);

  /** Emits when a message should be sent */
  sendMessage = output<string>();

  /** Current message content */
  protected message = signal<string>('');

  /** Whether the send button should be enabled */
  protected canSend = signal<boolean>(false);

  @ViewChild('textareaInput') private textareaInput!: ElementRef<HTMLTextAreaElement>;

  /**
   * Handles input changes to update state
   */
  onInput(event: Event): void {
    const textarea = event.target as HTMLTextAreaElement;
    this.message.set(textarea.value);
    this.canSend.set(textarea.value.trim().length > 0);
    this.adjustTextareaHeight(textarea);
  }

  /**
   * Handles keydown events for send shortcut
   * Enter sends message, Shift+Enter adds newline
   */
  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  /**
   * Sends the current message
   */
  send(): void {
    const content = this.message().trim();
    if (content && !this.disabled()) {
      this.sendMessage.emit(content);
      this.message.set('');
      this.canSend.set(false);
      this.resetTextareaHeight();
    }
  }

  /**
   * Adjusts textarea height based on content
   */
  private adjustTextareaHeight(textarea: HTMLTextAreaElement): void {
    textarea.style.height = 'auto';
    const maxHeight = 200; // Maximum height in pixels
    const newHeight = Math.min(textarea.scrollHeight, maxHeight);
    textarea.style.height = `${newHeight}px`;
  }

  /**
   * Resets textarea to initial height
   */
  private resetTextareaHeight(): void {
    const textarea = this.textareaInput?.nativeElement;
    if (textarea) {
      textarea.style.height = 'auto';
      textarea.value = '';
    }
  }

  /**
   * Focuses the textarea input
   */
  focus(): void {
    this.textareaInput?.nativeElement?.focus();
  }
}
