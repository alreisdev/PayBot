import { Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Message } from '../../../core/models/message.model';

/**
 * Component that displays a single chat message bubble
 */
@Component({
  selector: 'app-message-item',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './message-item.component.html',
  styleUrl: './message-item.component.scss'
})
export class MessageItemComponent {
  /** The message to display */
  message = input.required<Message>();

  /** Whether this is a user message */
  isUser = computed(() => this.message().role === 'user');

  /** Whether this is an assistant message */
  isAssistant = computed(() => this.message().role === 'assistant');

  /** CSS classes for the message container */
  containerClasses = computed(() => ({
    'message-container': true,
    'message-user': this.isUser(),
    'message-assistant': this.isAssistant()
  }));
}
