package com.agile.paybot.listener;

import com.agile.paybot.config.ChatQueueConfig;
import com.agile.paybot.domain.dto.ChatRequest;
import com.agile.paybot.domain.dto.ChatResponse;
import com.agile.paybot.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatTaskListener {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = ChatQueueConfig.CHAT_QUEUE)
    public void processChatRequest(ChatRequest request) {
        log.info("Processing chat request from queue: {}", request.message());

        try {
            ChatResponse response = chatService.processMessage(request);

            log.info("Sending response to WebSocket topic: {}", response.message().content());
            messagingTemplate.convertAndSend("/topic/messages", response);

        } catch (Exception e) {
            log.error("Error processing chat request: {}", e.getMessage(), e);
            // Send error response to WebSocket
            java.util.Map<String, String> errorResponse = java.util.Map.of(
                    "error", "Failed to process message: " + e.getMessage()
            );
            messagingTemplate.convertAndSend("/topic/messages", (Object) errorResponse);
        }
    }
}
