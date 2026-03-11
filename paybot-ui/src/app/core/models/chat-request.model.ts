/**
 * Represents a message in the conversation history sent to the API
 */
export interface ConversationMessage {
  role: 'user' | 'assistant';
  content: string;
}

/**
 * Request payload sent to the chat API
 */
export interface ChatRequest {
  /** The current user message */
  message: string;
  /** Unique request ID for idempotency */
  requestId: string;
  /** Session ID for server-side history management */
  sessionId: string;
  /** @deprecated No longer used - history is managed server-side */
  conversationHistory?: ConversationMessage[];
}
