/**
 * Represents a chat message in the conversation
 */
export interface Message {
  /** Unique identifier for the message */
  id: string;
  /** Role of the message sender */
  role: 'user' | 'assistant';
  /** Content of the message */
  content: string;
  /** Timestamp when the message was created */
  timestamp: Date;
}

/**
 * Creates a new message with a generated ID and current timestamp
 */
export function createMessage(
  role: 'user' | 'assistant',
  content: string
): Message {
  return {
    id: generateMessageId(),
    role,
    content,
    timestamp: new Date()
  };
}

/**
 * Generates a unique message ID
 */
function generateMessageId(): string {
  return `msg_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
}
