package com.agile.paybot.controller;

import com.agile.paybot.config.ChatQueueConfig;
import com.agile.paybot.shared.dto.ChatRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final RabbitTemplate rabbitTemplate;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Received chat request: requestId={}, sessionId={}", request.requestId(), request.sessionId());

        rabbitTemplate.convertAndSend(
                ChatQueueConfig.CHAT_EXCHANGE,
                ChatQueueConfig.CHAT_ROUTING_KEY,
                request
        );

        log.info("Chat request queued for processing");

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "Request accepted for processing"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "PayBot AI Service"
        ));
    }
}
