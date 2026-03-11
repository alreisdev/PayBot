package com.agile.paybot.listener;

import com.agile.paybot.config.ChatQueueConfig;
import com.agile.paybot.domain.dto.ChatRequest;
import com.agile.paybot.domain.dto.ChatResponse;
import com.agile.paybot.domain.dto.MessageDTO;
import com.agile.paybot.domain.entity.Payment;
import com.agile.paybot.service.ChatService;
import com.agile.paybot.service.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatTaskListener {

    private static final String IDEMPOTENCY_KEY_PREFIX = "chat:request:";
    private static final String RESPONSE_CACHE_KEY_PREFIX = "chat:response:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(60);

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final ChatService chatService;
    private final PaymentService paymentService;
    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = ChatQueueConfig.CHAT_QUEUE)
    public void processChatRequest(ChatRequest request, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        String requestId = request.requestId();
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + requestId;

        log.info("Received chat request: requestId={}, sessionId={}", requestId, request.sessionId());

        // Step 1: Try to acquire the idempotency lock via SETNX
        Boolean isNewRequest = stringRedisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, STATUS_PROCESSING, IDEMPOTENCY_TTL);

        if (Boolean.TRUE.equals(isNewRequest)) {
            // New request — process normally
            processNewRequest(request, channel, deliveryTag, idempotencyKey);
            return;
        }

        // Step 2: Key already exists — check its value
        String existingStatus = stringRedisTemplate.opsForValue().get(idempotencyKey);

        if (STATUS_COMPLETED.equals(existingStatus)) {
            // Already completed — replay cached response
            log.info("Request already completed, replaying cached response: requestId={}", requestId);
            replayCachedResponse(request, channel, deliveryTag);
            return;
        }

        // Step 3: Status is PROCESSING (stale from a crashed worker) — Double-Check against DB
        log.warn("Stale PROCESSING state detected for requestId={}, performing DB validation", requestId);
        handleStaleProcessing(request, channel, deliveryTag, idempotencyKey);
    }

    private void processNewRequest(ChatRequest request, Channel channel, long deliveryTag,
                                   String idempotencyKey) throws IOException {
        String requestId = request.requestId();

        log.info("Processing new chat request: requestId={}, message={}", requestId, request.message());

        try {
            ChatResponse response = chatService.processMessage(request);

            // Mark as completed in Redis
            stringRedisTemplate.opsForValue().set(idempotencyKey, STATUS_COMPLETED, IDEMPOTENCY_TTL);

            // Cache the response for replay
            cacheResponse(requestId, response);

            // Send response via WebSocket
            messagingTemplate.convertAndSend("/topic/messages/" + request.sessionId(), response);

            // Acknowledge the message
            channel.basicAck(deliveryTag, false);
            log.info("Successfully processed and acknowledged: requestId={}", requestId);

        } catch (Exception e) {
            log.error("Error processing chat request: requestId={}, error={}", requestId, e.getMessage(), e);

            // Delete the idempotency key to allow retry
            stringRedisTemplate.delete(idempotencyKey);

            // Send error response to WebSocket
            Map<String, String> errorResponse = Map.of(
                    "error", "Failed to process message: " + e.getMessage(),
                    "requestId", requestId
            );
            messagingTemplate.convertAndSend("/topic/messages/" + request.sessionId(), errorResponse);

            // Negative-acknowledge and requeue the message
            channel.basicNack(deliveryTag, false, true);
            log.warn("Nacked and requeued message: requestId={}", requestId);
        }
    }

    private void replayCachedResponse(ChatRequest request, Channel channel, long deliveryTag) throws IOException {
        String requestId = request.requestId();
        String responseKey = RESPONSE_CACHE_KEY_PREFIX + requestId;
        String cachedJson = stringRedisTemplate.opsForValue().get(responseKey);

        if (cachedJson != null) {
            try {
                ChatResponse cachedResponse = objectMapper.readValue(cachedJson, ChatResponse.class);
                messagingTemplate.convertAndSend("/topic/messages/" + request.sessionId(), cachedResponse);
                log.info("Replayed cached response via WebSocket: requestId={}", requestId);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached response for requestId={}: {}", requestId, e.getMessage());
            }
        } else {
            log.warn("No cached response found for completed request: requestId={}", requestId);
        }

        // Acknowledge regardless — the request was already completed
        channel.basicAck(deliveryTag, false);
    }

    private void handleStaleProcessing(ChatRequest request, Channel channel, long deliveryTag,
                                       String idempotencyKey) throws IOException {
        String requestId = request.requestId();

        // Check PostgreSQL for a payment record linked to this requestId
        Optional<Payment> existingPayment = paymentService.findPaymentByRequestId(requestId);

        if (existingPayment.isPresent()) {
            // Payment was made but Redis wasn't updated — self-heal
            Payment payment = existingPayment.get();
            log.info("DB validation found existing payment for requestId={}, confirmationNumber={}",
                    requestId, payment.getConfirmationNumber());

            // Build a recovery response from the DB record
            String recoveryMessage = String.format(
                    "Payment was already processed successfully. Confirmation Number: %s",
                    payment.getConfirmationNumber());
            ChatResponse recoveryResponse = new ChatResponse(
                    new MessageDTO("assistant", recoveryMessage),
                    new ChatResponse.ChatMetadata("gemini-2.0-flash", request.sessionId(), requestId, null)
            );

            // Update Redis to COMPLETED
            stringRedisTemplate.opsForValue().set(idempotencyKey, STATUS_COMPLETED, IDEMPOTENCY_TTL);
            cacheResponse(requestId, recoveryResponse);

            // Send the recovery response via WebSocket
            messagingTemplate.convertAndSend("/topic/messages/" + request.sessionId(), recoveryResponse);

            channel.basicAck(deliveryTag, false);
            log.info("Self-healed stale PROCESSING state for requestId={}", requestId);

        } else {
            // No payment in DB — the previous worker died before doing any work
            log.info("No payment found in DB for requestId={}, re-processing", requestId);

            // Delete stale key and re-acquire
            stringRedisTemplate.delete(idempotencyKey);
            Boolean reacquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(idempotencyKey, STATUS_PROCESSING, IDEMPOTENCY_TTL);

            if (Boolean.TRUE.equals(reacquired)) {
                processNewRequest(request, channel, deliveryTag, idempotencyKey);
            } else {
                // Another worker grabbed it — let them handle it
                log.info("Another worker acquired the lock for requestId={}, nacking", requestId);
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }

    private void cacheResponse(String requestId, ChatResponse response) {
        String responseKey = RESPONSE_CACHE_KEY_PREFIX + requestId;
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(responseKey, responseJson, IDEMPOTENCY_TTL);
            log.debug("Cached response for requestId={}", requestId);
        } catch (JsonProcessingException e) {
            log.error("Failed to cache response for requestId={}: {}", requestId, e.getMessage());
        }
    }
}
