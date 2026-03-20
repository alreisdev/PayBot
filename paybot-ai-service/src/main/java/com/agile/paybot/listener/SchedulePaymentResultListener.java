package com.agile.paybot.listener;

import com.agile.paybot.config.ChatQueueConfig;
import static com.agile.paybot.config.RedisKeyConstants.*;
import com.agile.paybot.shared.dto.ChatResponse;
import com.agile.paybot.shared.dto.MessageDTO;
import com.agile.paybot.shared.event.SchedulePaymentResultEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchedulePaymentResultListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.google.genai.chat.options.model}")
    private String modelVersion;

    @RabbitListener(queues = ChatQueueConfig.SCHEDULE_RESULT_QUEUE)
    public void handleScheduleResult(SchedulePaymentResultEvent event, Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {

        String requestId = event.requestId();
        String sessionId = event.sessionId();

        log.info("Received schedule payment result: requestId={}, success={}, scheduledPaymentId={}",
                requestId, event.success(), event.scheduledPaymentId());

        try {
            String message = event.success()
                    ? event.message()
                    : String.format("Failed to schedule payment: %s", event.message());

            ChatResponse response = new ChatResponse(
                    new MessageDTO("assistant", message),
                    new ChatResponse.ChatMetadata(modelVersion, sessionId, requestId, null, false)
            );

            messagingTemplate.convertAndSend("/topic/messages/" + sessionId, response);

            String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + requestId;
            stringRedisTemplate.opsForValue().set(idempotencyKey, "COMPLETED", IDEMPOTENCY_TTL);
            cacheResponse(requestId, response);

            channel.basicAck(tag, false);
            log.info("Schedule payment result delivered via WebSocket: requestId={}, sessionId={}",
                    requestId, sessionId);

        } catch (Exception e) {
            log.error("Error handling schedule payment result: requestId={}, error={}",
                    requestId, e.getMessage(), e);
            channel.basicNack(tag, false, true);
        }
    }

    private void cacheResponse(String requestId, ChatResponse response) {
        String responseKey = RESPONSE_CACHE_KEY_PREFIX + requestId;
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(responseKey, responseJson, IDEMPOTENCY_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to cache schedule payment result for requestId={}: {}", requestId, e.getMessage());
        }
    }
}
