package com.agile.paybot.config;

/**
 * ThreadLocal holder for the current request's ID.
 * Used to pass requestId from ChatTaskListener through ChatService to PayBotTools
 * without modifying Gemini's function calling signatures.
 */
public class RequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static String getRequestId() {
        return REQUEST_ID.get();
    }

    public static void clear() {
        REQUEST_ID.remove();
    }
}
