import {
  Component,
  ElementRef,
  ViewChild,
  AfterViewChecked,
  input
} from '@angular/core';
import { Message } from '../../../core/models/message.model';
import { MessageItemComponent } from '../message-item/message-item.component';

/**
 * Component that displays a scrollable list of chat messages
 * with auto-scroll to bottom on new messages
 */
@Component({
  selector: 'app-message-list',
  standalone: true,
  imports: [MessageItemComponent],
  templateUrl: './message-list.component.html',
  styleUrl: './message-list.component.scss'
})
export class MessageListComponent implements AfterViewChecked {
  /** List of messages to display */
  messages = input.required<Message[]>();

  @ViewChild('scrollContainer') private scrollContainer!: ElementRef<HTMLDivElement>;

  private shouldScroll = true;

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollToBottom();
    }
  }

  /**
   * Scrolls the message container to the bottom
   */
  private scrollToBottom(): void {
    try {
      const container = this.scrollContainer?.nativeElement;
      if (container) {
        container.scrollTop = container.scrollHeight;
      }
    } catch (err) {
      console.error('Error scrolling to bottom:', err);
    }
  }

  /**
   * Handles scroll events to determine if auto-scroll should continue
   */
  onScroll(): void {
    const container = this.scrollContainer?.nativeElement;
    if (container) {
      const threshold = 100;
      const position = container.scrollTop + container.clientHeight;
      const height = container.scrollHeight;
      this.shouldScroll = position > height - threshold;
    }
  }

  /**
   * Track function for ngFor to optimize rendering
   */
  trackByMessageId(_index: number, message: Message): string {
    return message.id;
  }
}
