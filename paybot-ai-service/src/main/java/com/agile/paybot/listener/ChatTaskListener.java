package com.agile.paybot.listener;

import com.agile.paybot.client.FinancialClient;
import com.agile.paybot.config.ChatQueueConfig;
import static com.agile.paybot.config.RedisKeyConstants.*;
import com.agile.paybot.service.ChatService;
import com.agile.paybot.shared.dto.ChatRequest;
import com.agile.paybot.shared.dto.ChatResponse;
import com.agile.paybot.shared.dto.MessageDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatTaskListener {

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final int MAX_RETRIES = 3;

    private final ChatService chatService;
    private final FinancialClient financialClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.google.genai.chat.options.model}")
    private String modelVersion;

    @RabbitListener(queues = ChatQueueConfig.CHAT_QUEUE)
    public void processChatRequest(ChatRequest request, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                   MessageHeaders headers) throws IOException {
        String requestId = request.requestId();
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + requestId;
        int retryCount = getRetryCount(headers);

        log.info("Received chat request: requestId={}, sessionId={}, retryCount={}",
                requestId, request.sessionId(), retryCount);

        // Step 1: Try to acquire the idempotency lock via SETNX
        Boolean isNewRequest = stringRedisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, STATUS_PROCESSING, IDEMPOTENCY_TTL);

        if (Boolean.TRUE.equals(isNewRequest)) {
            processNewRequest(request, channel, deliveryTag, idempotencyKey, retryCount);
            return;
        }

        // Step 2: Key already exists — check its value
        String existingStatus = stringRedisTemplate.opsForValue().get(idempotencyKey);

        if (STATUS_COMPLETED.equals(existingStatus)) {
            log.info("Request already completed, replaying cached response: requestId={}", requestId);
            replayCachedResponse(request, channel, deliveryTag);
            return;
        }

        // Step 3: Status is PROCESSING (stale from a crashed worker)
        // Financial service owns payment idempotency now via DB check,
        // so we simply delete the stale key and re-process
        log.warn("Stale PROCESSING state detected for requestId={}, re-acquiring lock", requestId);
        handleStaleProcessing(request, channel, deliveryTag, idempotencyKey, retryCount);
    }

    private void processNewRequest(ChatRequest request, Channel channel, long deliveryTag,
                                   String idempotencyKey, int retryCount) throws IOException {
        String requestId = request.requestId();

        log.info("Processing new chat request: requestId={}, retryCount={}", requestId, retryCount);

        try {
            ChatResponse response = chatService.processMessage(request);

            // Mark as completed in Redis
            stringRedisTemplate.opsForValue().set(idempotencyKey, STATUS_COMPLETED, IDEMPOTENCY_TTL);

            // Cache the response for replay
            cacheResponse(requestId, response);

            // Skip sending the LLM response if a payment was triggered —
            // the saga result listener will send the detailed confirmation via WebSocket
            if (Boolean.TRUE.equals(response.metadata().paymentTriggered())) {
                log.info("Payment triggered, skipping LLM response WebSocket send: requestId={}", requestId);
            } else {
                messagingTemplate.convertAndSend("/topic/messages/" + request.sessionId(), response);
            }

            // Acknowledge the message
            channel.basicAck(deliveryTag, false);
            log.info("Successfully processed and acknowledged: requestId={}", requestId);

        } catch (Exception e) {
            log.error("Error processing chat request: requestId={}, retryCount={}, error={}",
                    requestId, retryCount, e.getMessage(), e);

            // Delete the idempotency key to allow retry
            stringRedisTemplate.delete(idempotencyKey);

            handleFailure(request, channel, deliveryTag, retryCount, e);
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

        channel.basicAck(deliveryTag, false);
    }

    private void handleStaleProcessing(ChatRequest request, Channel channel, long deliveryTag,
                                       String idempotencyKey, int retryCount) throws IOException {
        String requestId = request.requestId();

        // Double-check: verify against financial service DB if a payment was already processed
        try {
            Boolean alreadyProcessed = financialClient.paymentExistsByRequestId(requestId);
            if (Boolean.TRUE.equals(alreadyProcessed)) {
                log.info("Payment already exists in financial DB for requestId={}, marking as completed", requestId);
                stringRedisTemplate.opsForValue().set(idempotencyKey, STATUS_COMPLETED, IDEMPOTENCY_TTL);
                channel.basicAck(deliveryTag, false);
                return;
            }
        } catch (Exception e) {
            log.warn("Could not verify payment status for requestId={}: {}", requestId, e.getMessage());
        }

        // Atomically overwrite stale key with PROCESSING status (avoids delete+setNX race)
        stringRedisTemplate.opsForValue().set(idempotencyKey, STATUS_PROCESSING, IDEMPOTENCY_TTL);
        // Since we just overwrote it, we own the lock now
        {
            processNewRequest(request, channel, deliveryTag, idempotencyKey, retryCount);
        }
    }

    private void handleFailure(ChatRequest request, Channel channel, long deliveryTag,
                               int retryCount, Exception e) throws IOException {
        String requestId = request.requestId();

        if (retryCount < MAX_RETRIES) {
            log.warn("Retry {}/{} for requestId={}, requeueing message", retryCount + 1, MAX_RETRIES, requestId);
            channel.basicNack(deliveryTag, false, true);
        } else {
            log.error("POISON PILL detected: requestId={} failed after {} retries, routing to DLQ. Error: {}",
                    requestId, MAX_RETRIES, e.getMessage());

            ChatResponse errorResponse = new ChatResponse(
                    new MessageDTO("assistant",
                            "I'm having trouble processing this specific request. Please try rephrasing."),
                    new ChatResponse.ChatMetadata(modelVersion, request.sessionId(), requestId, null, false)
            );
            messagingTemplate.convertAndSend("/topic/messages/" + request.sessionId(), errorResponse);

            channel.basicNack(deliveryTag, false, false);
        }
    }

    @SuppressWarnings("unchecked")
    private int getRetryCount(MessageHeaders headers) {
        List<Map<String, ?>> xDeath = (List<Map<String, ?>>) headers.get("x-death");
        if (xDeath == null || xDeath.isEmpty()) {
            return 0;
        }
        Object count = xDeath.get(0).get("count");
        if (count instanceof Number) {
            return ((Number) count).intValue();
        }
        return 0;
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
