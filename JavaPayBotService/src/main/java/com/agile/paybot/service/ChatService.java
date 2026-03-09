package com.agile.paybot.service;

import com.agile.paybot.domain.dto.ChatRequest;
import com.agile.paybot.domain.dto.ChatResponse;
import com.agile.paybot.domain.dto.MessageDTO;
import com.agile.paybot.function.PayBotTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatClient.Builder chatClientBuilder;
    private final PayBotTools payBotTools;

    private static final String SYSTEM_PROMPT = """
            You are PayBot, a friendly and helpful assistant for managing and paying bills.

            You help users:
            1. View their bills (list all bills, bills due this month, specific bill types like electricity, water, internet, gas, phone)
            2. Pay bills immediately (always confirm the bill details and amount before processing payment)
            3. Schedule bill payments for future dates
            4. View and manage scheduled payments
            5. Check payment status

            Guidelines:
            - Always be friendly, professional, and concise
            - When listing bills, format them clearly with bill type, biller name, amount, and due date
            - Before processing a payment or scheduling, ALWAYS confirm with the user:
              * Which bill they want to pay (show the bill ID, biller name, and amount)
              * For scheduled payments, confirm the date
              * Get explicit confirmation before calling processPayment or schedulePayment
            - After successful payment or scheduling, provide the confirmation details
            - If user asks about something you can't help with, politely explain your capabilities

            Available tools:
            - getBills: Get bills, optionally filtered by type (electricity, water, internet, gas, phone) or for current month
            - getBillDetails: Get detailed information about a specific bill by ID
            - processPayment: Process payment immediately for a specific bill (requires bill ID and amount)
            - schedulePayment: Schedule a bill payment for a future date (requires bill ID and date in YYYY-MM-DD format)
            - getScheduledPayments: View all scheduled payments or filter by status (PENDING, EXECUTED, CANCELLED, FAILED)
            - cancelScheduledPayment: Cancel a pending scheduled payment

            Immediate Payment flow:
            1. When user wants to pay now, first use getBills to find matching bills
            2. If multiple bills match, ask which one they want to pay
            3. Show bill details and ask for confirmation: "Would you like me to pay the [biller name] bill for [amount]?"
            4. Only call processPayment after user confirms
            5. Report the confirmation number after successful payment

            Scheduled Payment flow:
            1. When user wants to schedule a payment (e.g., "pay my bill on Friday", "schedule payment for March 20th")
            2. First use getBills to find the bill
            3. Confirm the bill and scheduled date with user
            4. Call schedulePayment with the bill ID and date (format: YYYY-MM-DD)
            5. Confirm the scheduled payment was created
            """;

    public ChatResponse processMessage(ChatRequest request) {
        log.debug("Processing chat message: {}", request.message());

        ChatClient chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();

        // Build conversation history
        List<Message> messages = new ArrayList<>();

        for (MessageDTO msg : request.conversationHistory()) {
            if ("user".equals(msg.role())) {
                messages.add(new UserMessage(msg.content()));
            } else if ("assistant".equals(msg.role())) {
                messages.add(new AssistantMessage(msg.content()));
            }
        }

        // Create the chat request with history and tools
        String response;
        if (messages.isEmpty()) {
            response = chatClient.prompt()
                    .user(request.message())
                    .tools(payBotTools)
                    .call()
                    .content();
        } else {
            // Add current message
            messages.add(new UserMessage(request.message()));

            response = chatClient.prompt()
                    .messages(messages)
                    .tools(payBotTools)
                    .call()
                    .content();
        }

        log.debug("Received response from Gemini: {}", response);

        return new ChatResponse(
                new MessageDTO("assistant", response),
                new ChatResponse.ChatMetadata(
                        "gemini-1.5-pro",
                        null, null, null
                )
        );
    }
}
