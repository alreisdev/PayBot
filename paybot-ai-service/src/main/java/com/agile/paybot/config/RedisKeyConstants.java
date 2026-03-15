package com.agile.paybot.config;

import java.time.Duration;

/**
 * Shared Redis key prefixes and TTL values used across listeners.
 * Centralizes these constants to prevent divergence across ChatTaskListener,
 * PaymentResultListener, and SchedulePaymentResultListener.
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {
        // prevent instantiation
    }

    public static final String IDEMPOTENCY_KEY_PREFIX = "chat:request:";
    public static final String RESPONSE_CACHE_KEY_PREFIX = "chat:response:";
    public static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(60);
}
