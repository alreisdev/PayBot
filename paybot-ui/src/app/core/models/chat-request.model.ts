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
  /** Previous conversation history for context */
  conversationHistory: ConversationMessage[];
}
