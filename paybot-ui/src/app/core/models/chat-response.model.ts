/**
 * Response message from the chat API
 */
export interface ResponseMessage {
  role: 'assistant';
  content: string;
}

/**
 * Metadata about the chat response
 */
export interface ChatMetadata {
  /** Processing time in milliseconds */
  processingTime?: number;
  /** Model used for generating the response */
  model?: string;
  /** Any additional metadata */
  [key: string]: unknown;
}

/**
 * Response payload from the chat API
 */
export interface ChatResponse {
  /** The assistant's response message */
  message: ResponseMessage;
  /** Optional metadata about the response */
  metadata?: ChatMetadata;
}
