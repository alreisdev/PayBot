package com.agile.paybot.shared.constants;

/**
 * Shared constants across PayBot services.
 */
public final class PayBotConstants {

    private PayBotConstants() {
        // prevent instantiation
    }

    /**
     * Default user ID used until real authentication is implemented.
     * TODO: Replace with authenticated user identity from SecurityContext
     */
    public static final String DEFAULT_USER_ID = "user-1";
}
