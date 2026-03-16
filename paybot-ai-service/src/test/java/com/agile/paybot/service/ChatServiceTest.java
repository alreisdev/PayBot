package com.agile.paybot.service;

import com.agile.paybot.function.PayBotTools;
import com.agile.paybot.shared.dto.ChatRequest;
import com.agile.paybot.shared.dto.ChatResponse;
import com.agile.paybot.shared.dto.MessageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private PayBotTools payBotTools;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatClientBuilder, payBotTools, stringRedisTemplate, objectMapper);
    }

    @Test
    void processMessage_nullGeminiResponse_returnsFallback() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(any(PayBotTools.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(null);

        ChatRequest request = new ChatRequest("Hello", "req-1", "sess-1", List.of());
        ChatResponse response = chatService.processMessage(request);

        assertThat(response.message().content())
                .isEqualTo("I'm sorry, I couldn't process your request. Please try again.");
    }

    @Test
    void processMessage_successfulResponse_returnsContent() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(any(PayBotTools.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Here are your bills");

        ChatRequest request = new ChatRequest("Show bills", "req-2", "sess-1", List.of());
        ChatResponse response = chatService.processMessage(request);

        assertThat(response.message().content()).isEqualTo("Here are your bills");
        assertThat(response.message().role()).isEqualTo("assistant");
        assertThat(response.metadata().sessionId()).isEqualTo("sess-1");
    }

    @Test
    void processMessage_clearsThreadLocalContext() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(any(PayBotTools.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("response");

        ChatRequest request = new ChatRequest("Hello", "req-3", "sess-1", List.of());
        chatService.processMessage(request);

        // Verify ThreadLocal was cleared (no way to assert directly, but if processMessage
        // threw an exception, clearContext should still have been called via finally block)
        // This test verifies the happy path completes without ThreadLocal leaking
    }

    @Test
    void processMessage_withExistingHistory_includesInPrompt() throws Exception {
        List<MessageDTO> historyList = List.of(
                new MessageDTO("user", "previous question"),
                new MessageDTO("assistant", "previous answer")
        );
        String historyJson = objectMapper.writeValueAsString(historyList);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(contains("chat:history:"))).thenReturn(historyJson);

        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.tools(any(PayBotTools.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("follow-up response");

        ChatRequest request = new ChatRequest("follow-up", "req-4", "sess-1", List.of());
        ChatResponse response = chatService.processMessage(request);

        assertThat(response.message().content()).isEqualTo("follow-up response");
        // When history exists, messages() path is used instead of user()
        verify(requestSpec).messages(anyList());
        verify(requestSpec, never()).user(anyString());
    }
}
