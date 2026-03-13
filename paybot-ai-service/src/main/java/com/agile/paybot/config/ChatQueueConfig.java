package com.agile.paybot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatQueueConfig {

    // ── Chat request queue (existing) ──
    public static final String CHAT_QUEUE = "chat.requests";
    public static final String CHAT_EXCHANGE = "chat.exchange";
    public static final String CHAT_ROUTING_KEY = "chat.request";

    // ── Chat DLQ (existing) ──
    public static final String ERROR_QUEUE = "chat.requests.error";
    public static final String DLX_EXCHANGE = "chat.requests.dlx";
    public static final String DLX_ROUTING_KEY = "error";

    // ── Financial saga queues (shared declarations with financial service) ──
    public static final String FINANCIAL_EXCHANGE = "financial.exchange";
    public static final String PAYMENT_COMMAND_QUEUE = "financial.payment.command";
    public static final String PAYMENT_COMMAND_KEY = "payment.command";
    public static final String PAYMENT_RESULT_QUEUE = "financial.payment.result";
    public static final String PAYMENT_RESULT_KEY = "payment.result";
    public static final String SCHEDULE_COMMAND_QUEUE = "financial.schedule.command";
    public static final String SCHEDULE_COMMAND_KEY = "schedule.command";
    public static final String SCHEDULE_RESULT_QUEUE = "financial.schedule.result";
    public static final String SCHEDULE_RESULT_KEY = "schedule.result";

    // ── Chat queue beans ──

    @Bean
    Queue chatQueue() {
        return QueueBuilder.durable(CHAT_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    DirectExchange chatExchange() {
        return new DirectExchange(CHAT_EXCHANGE);
    }

    @Bean
    Binding chatBinding(Queue chatQueue, DirectExchange chatExchange) {
        return BindingBuilder
                .bind(chatQueue)
                .to(chatExchange)
                .with(CHAT_ROUTING_KEY);
    }

    @Bean
    Queue errorQueue() {
        return QueueBuilder.durable(ERROR_QUEUE).build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    Binding errorBinding(Queue errorQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder
                .bind(errorQueue)
                .to(deadLetterExchange)
                .with(DLX_ROUTING_KEY);
    }

    // ── Financial saga queue beans (RabbitMQ is idempotent on duplicate declarations) ──

    @Bean
    DirectExchange financialExchange() {
        return new DirectExchange(FINANCIAL_EXCHANGE);
    }

    @Bean
    Queue paymentCommandQueue() {
        return QueueBuilder.durable(PAYMENT_COMMAND_QUEUE).build();
    }

    @Bean
    Queue paymentResultQueue() {
        return QueueBuilder.durable(PAYMENT_RESULT_QUEUE).build();
    }

    @Bean
    Binding paymentCommandBinding(Queue paymentCommandQueue, DirectExchange financialExchange) {
        return BindingBuilder
                .bind(paymentCommandQueue)
                .to(financialExchange)
                .with(PAYMENT_COMMAND_KEY);
    }

    @Bean
    Binding paymentResultBinding(Queue paymentResultQueue, DirectExchange financialExchange) {
        return BindingBuilder
                .bind(paymentResultQueue)
                .to(financialExchange)
                .with(PAYMENT_RESULT_KEY);
    }

    @Bean
    Queue scheduleCommandQueue() {
        return QueueBuilder.durable(SCHEDULE_COMMAND_QUEUE).build();
    }

    @Bean
    Queue scheduleResultQueue() {
        return QueueBuilder.durable(SCHEDULE_RESULT_QUEUE).build();
    }

    @Bean
    Binding scheduleCommandBinding(Queue scheduleCommandQueue, DirectExchange financialExchange) {
        return BindingBuilder
                .bind(scheduleCommandQueue)
                .to(financialExchange)
                .with(SCHEDULE_COMMAND_KEY);
    }

    @Bean
    Binding scheduleResultBinding(Queue scheduleResultQueue, DirectExchange financialExchange) {
        return BindingBuilder
                .bind(scheduleResultQueue)
                .to(financialExchange)
                .with(SCHEDULE_RESULT_KEY);
    }

    // ── Shared infrastructure ──

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }
}
